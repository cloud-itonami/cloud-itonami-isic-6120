(ns wirelesstelecom.opsadvisor
  "Network Operations Advisor client -- the *contained intelligence
  node* for the wireless-carrier actor (named in this repo's own
  README `Core Contract` diagram).

  It normalizes subscriber-line intake, drafts a per-jurisdiction
  identity-verification evidence checklist (subscriber-identity +
  spectrum-license spec-basis), screens a line's site for an unresolved
  spectrum-license dispute, drafts the MSISDN-provisioning action, and
  drafts the service-suspension action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a real
  MSISDN provisioning/service suspension. Every output is censored
  downstream by `wirelesstelecom.governor` before anything touches the
  SSoT, and `:actuation/provision-msisdn`/`:actuation/suspend-service`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Out of scope, by construction: this advisor has NO dispatch for
  lawful-intercept, subscriber-location disclosure, or law-enforcement-
  ordered suspension -- see this repo's `docs/business-model.md` Trust
  Controls and `wirelesstelecom.governor`'s own ns docstring 'Out of
  scope' note. `infer` below routes only the five ops this actor
  actually governs.

  Like every sibling actor's advisor (including `cloud-itonami-isic-
  6190`'s own `telecom.telecomadvisor`), this is a deterministic mock
  so the actor graph runs offline and the governor contract is
  exercised end-to-end. In production this calls a real LLM
  (kotoba-llm or equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/provision-msisdn | :actuation/suspend-service | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [wirelesstelecom.facts :as facts]
            [wirelesstelecom.registry :as registry]
            [wirelesstelecom.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the line, MSISDN figures or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "回線記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :line/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-identity
  "Per-jurisdiction subscriber-identity/spectrum-licensing evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `wirelesstelecom.facts` -- the Mobile Network Governor
  must reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [ln (store/line db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction ln))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "wirelesstelecom.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-license-dispute
  "Spectrum-license-dispute screening draft. `:license-dispute-
  unresolved?` on the line record injects the failure mode: the Mobile
  Network Governor must HOLD, un-overridably, on any unresolved
  dispute."
  [db {:keys [subject]}]
  (let [ln (store/line db subject)]
    (cond
      (nil? ln)
      {:summary "対象回線記録が見つかりません" :rationale "no line record"
       :cites [] :effect :license-screen/set :value {:line-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:license-dispute-unresolved? ln))
      {:summary    (str (:holder-name ln) ": 未解決の周波数割当紛争を検出")
       :rationale  "スクリーニングが未解決の周波数割当紛争を検出。人手確認とホールドが必須。"
       :cites      [:license-check]
       :effect     :license-screen/set
       :value      {:line-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:holder-name ln) ": 未解決の周波数割当紛争なし")
       :rationale  "周波数割当紛争スクリーニング完了。"
       :cites      [:license-check]
       :effect     :license-screen/set
       :value      {:line-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-msisdn-provisioning
  "Draft the actual MSISDN-PROVISIONING action -- provisioning a real
  mobile subscriber number + SIM for a line. ALWAYS `:stake
  :actuation/provision-msisdn` -- this is a REAL-WORLD act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`wirelesstelecom.phase`); the
  governor also always escalates on `:actuation/provision-msisdn`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [ln (store/line db subject)]
    {:summary    (str subject " 向けMSISDN発行提案"
                      (when ln (str " (line=" (:holder-name ln) ")")))
     :rationale  (if ln
                   (str "msisdn=" (:msisdn ln))
                   "回線記録が見つかりません")
     :cites      (if ln [subject] [])
     :effect     :line/mark-provisioned
     :value      {:line-id subject}
     :stake      :actuation/provision-msisdn
     :confidence (if (and ln (not (registry/msisdn-invalid-format? ln))) 0.9 0.3)}))

(defn- propose-service-suspension
  "Draft the actual SERVICE-SUSPENSION action -- suspending a real
  subscriber's wireless service (e.g. for non-payment). ALWAYS `:stake
  :actuation/suspend-service` -- this is a REAL-WORLD act (and, like
  `telecom.telecomadvisor`'s billing-record suppression, a NEGATIVE
  one -- withholding ongoing connectivity, not issuing it), never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`wirelesstelecom.phase`); the
  governor also always escalates on `:actuation/suspend-service`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [ln (store/line db subject)]
    {:summary    (str subject " 向け回線停止提案"
                      (when ln (str " (line=" (:holder-name ln) ")")))
     :rationale  (if ln
                   "jurisdiction-evidence-checklist referenced"
                   "回線記録が見つかりません")
     :cites      (if ln [subject] [])
     :effect     :line/mark-suspended
     :value      {:line-id subject}
     :stake      :actuation/suspend-service
     :confidence (if ln 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :line/intake                      (normalize-intake db request)
    :identity/verify                  (verify-identity db request)
    :license/screen                   (screen-license-dispute db request)
    :actuation/provision-msisdn       (propose-msisdn-provisioning db request)
    :actuation/suspend-service        (propose-service-suspension db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは移動体通信事業者のMSISDN発行・回線停止エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:line/upsert|:verification/set|:license-screen/set|"
       ":line/mark-provisioned|:line/mark-suspended) "
       ":stake(:actuation/provision-msisdn か :actuation/suspend-service か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :identity/verify                   {:line (store/line st subject)}
    :license/screen                    {:line (store/line st subject)}
    :actuation/provision-msisdn        {:line (store/line st subject)}
    :actuation/suspend-service         {:line (store/line st subject)}
    {:line (store/line st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Mobile Network Governor
  escalates/holds -- an LLM hiccup can never auto-provision an MSISDN
  or auto-suspend service."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :opsadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
