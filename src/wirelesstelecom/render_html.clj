(ns wirelesstelecom.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: there was no demo page
  and no generator here before. This namespace drives the REAL actor
  stack -- `wirelesstelecom.operation` (the compiled langgraph
  StateGraph) -> `wirelesstelecom.governor` -> `wirelesstelecom.phase`
  -> `wirelesstelecom.store` -- and renders whatever that run actually
  produced. Nothing on the page is hand-typed: every id, count,
  disposition, rule name and Japanese violation detail is read back out
  of the run result or the append-only ledger.

  Provenance of every value fed IN (nothing here is invented):
    - the three registered sites are byte-for-byte this repo's own demo
      seed in `wirelesstelecom.sim/demo` (site-001 / site-002 /
      site-003, with their names and their
      `:spectrum-license-status` / `:site-access-record` fields)
    - `site-999` (unregistered), the `2500` backhaul-equipment order,
      the `\"RFインターフェアランスの可能性\"` concern and
      `subscriber-ref \"sub-778\"` also come from `wirelesstelecom.sim`
    - `\"AAA+\"` (unrecognized build-status), `:dispatch-drone-survey`
      (out-of-allowlist op), `\"meter-reading-2026-07\"` (usage
      evidence) and the 600 / 2200 order costs come from this repo's own
      `test/wirelesstelecom/governor_test.cljc`
    - equipment categories + thresholds, build-status vocabulary and
      site-operation types are read at render time from
      `wirelesstelecom.facts`; the op allowlist, blocked ops,
      always-escalate ops and the confidence floor from
      `wirelesstelecom.governor`
    - the ONLY authored component is `rogue-advisor` (step `h10`), a
      deliberately broken advisor injected through the public
      `operation/build` `:advisor` seam to reach the `:no-execution`
      rule the shipped mock advisor cannot trigger. It is an INPUT
      (a fault to be censored), never an output -- the resulting hold
      is the real Governor's own verdict.

  Deterministic by construction: no clock reads, no randomness, no
  reliance on map iteration order (every collection rendered is either
  an explicitly ordered vector or explicitly sorted). Two runs are
  byte-identical.

  `-main` REFUSES to write the file when the run produced no HARD
  governor hold -- a console that shows no real hold would be
  indistinguishable from a hand-written mock.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [wirelesstelecom.advisor :as advisor]
            [wirelesstelecom.facts :as facts]
            [wirelesstelecom.governor :as governor]
            [wirelesstelecom.operation :as operation]
            [wirelesstelecom.store :as store]))

;; ----------------------------- scenario input -----------------------------

(def ^:private seed-sites
  "Exactly the site register this repo's own demo driver
  (`wirelesstelecom.sim/demo`) seeds -- same ids, same names, same
  `:spectrum-license-status` / `:site-access-record` values. Held as an
  ordered vector (not a map) so the rendered site register has a stable
  order independent of hash-map iteration."
  [["site-001" {:id "site-001"
                :name "North Ridge Tower"
                :spectrum-license-status :active
                :site-access-record true}]
   ["site-002" {:id "site-002"
                :name "Harbor District Tower (license renewal pending)"
                :spectrum-license-status :pending
                :site-access-record true}]
   ["site-003" {:id "site-003"
                :name "West Valley Tower (site-access not yet secured)"
                :spectrum-license-status :active
                :site-access-record false}]])

(def ^:private netops-3
  "Phase 3 (full autonomy) operator context -- `wirelesstelecom.sim`'s
  own `operator` map."
  {:actor-id "netops-01" :role :network-operator :phase :phase-3})

(def ^:private netops-1 (assoc netops-3 :phase :phase-1))
(def ^:private netops-0 (assoc netops-3 :phase :phase-0))

(def ^:private approved {:status :approved :by "netops-01"})
(def ^:private rejected {:status :rejected :by "netops-01"})

(def ^:private rogue-advisor
  "A deliberately MALFUNCTIONING advisor, injected over the SAME store
  through the `:advisor` seam `wirelesstelecom.operation/build` already
  exposes. It claims `:effect :actuate` -- a real-world actuation --
  which the shipped `wirelesstelecom.advisor/mock-advisor` can never
  emit (every one of its branches hard-codes `:effect :propose`).

  Without this injection the Governor's `:no-execution` rule is
  structurally unreachable, and the console could only claim in prose
  that the rule exists. Injecting a broken advisor is the only honest
  way to DEMONSTRATE it: the proposal targets `site-001` (registered)
  and carries neither `:equipment-count` nor `:build-status`, so the
  other eight checks stay silent and `:no-execution` is the sole
  violation -- the hold is attributable to this rule and nothing else.

  This is the defense-in-depth claim the whole actor rests on: a
  compromised, hallucinating or simply buggy intelligence layer gains
  no authority, because the Governor re-derives the verdict from the
  proposal rather than trusting it."
  (reify advisor/Advisor
    (-advise [_ _ request]
      {:op         (:op request)
       :effect     :actuate
       :value      {:site-id (:site-id request)}
       :cites      ["operator-submitted-site-data"]
       :summary    "ROGUE advisor: 実世界の作動（:effect :actuate）を主張する提案"
       :confidence 0.99})))

(def ^:private scenario
  "The scenario, as data, so the rendered run table IS the scenario --
  no second hand-maintained description to drift out of sync.

  Covers: a full clean phase-3 lifecycle on site-001; two distinct
  human-approved escalations (always-escalate network fault, and a
  category-threshold cost escalation); a human-REJECTED escalation; both
  phase-gate escalation reasons (`:phase-0-simulation-only`,
  `:phase-1-always-escalate`); and ALL NINE of this Governor's hard
  rules.

  The ninth, `:no-execution`, is unreachable through the shipped
  `wirelesstelecom.advisor/mock-advisor` (every branch of which
  hard-codes `:effect :propose`), so step `h10` injects `rogue-advisor`
  over the SAME store through the `:advisor` seam
  `wirelesstelecom.operation/build` already exposes. That is the only
  honest way to demonstrate the rule rather than assert it in prose."
  [;; -- clean phase-3 lifecycle on site-001 -----------------------------
   {:tid "s01" :context netops-3
    :note "登録済みサイトの建設記録ログ（governor clean → 自動コミット）"
    :request {:op :log-network-build-record :site-id "site-001"
              :equipment-count 3 :build-status "operational"
              :build-type "deployment"}}
   {:tid "s02" :context netops-3
    :note "点検作業のスケジュール（ルーチン調整 op）"
    :request {:op :schedule-site-operation :site-id "site-001"
              :operation-type "inspection" :reason "routine-schedule"}}
   {:tid "s03" :context netops-3
    :note "spectrum-license-status :active の確認後に稼働開始"
    :request {:op :activate-tower :site-id "site-001"}}
   {:tid "s04" :context netops-3
    :note "site-access-record 確認後の契約者収容"
    :request {:op :provision-subscriber :site-id "site-001"
              :subscriber-ref "sub-778" :service-type "voice-data"}}
   {:tid "s05" :context netops-3
    :note "利用実績エビデンスを引用した請求記録（:amount は指定せず advisor 既定値 0）"
    :request {:op :log-billing-record :site-id "site-001"
              :usage-evidence ["meter-reading-2026-07"]}}
   {:tid "s06" :context netops-3
    :note "rf-equipment 閾値 800 未満の発注 → エスカレーション不要"
    :request {:op :order-equipment :site-id "site-001"
              :category "rf-equipment" :cost 600}}

   ;; -- escalations that DO reach a human --------------------------------
   {:tid "s07" :context netops-3 :approval approved
    :note "ネットワーク障害フラグは常に人間の承認が必要（always-escalate）"
    :request {:op :flag-network-fault :site-id "site-001"
              :concern "RFインターフェアランスの可能性"}}
   {:tid "s08" :context netops-3 :approval approved
    :note "backhaul-equipment 閾値 2000 超過 → 承認後にコミット"
    :request {:op :order-equipment :site-id "site-001"
              :category "backhaul-equipment" :cost 2200}}
   {:tid "s09" :context netops-3 :approval rejected
    :note "同じく閾値超過だが、運用者が却下 → ホールド"
    :request {:op :order-equipment :site-id "site-001"
              :category "backhaul-equipment" :cost 2500}}

   ;; -- phase gate --------------------------------------------------------
   {:tid "s10" :context netops-0 :approval approved
    :note "phase-0（シミュレーション）では governor clean でも自動コミットしない"
    :request {:op :log-network-build-record :site-id "site-001"
              :equipment-count 3 :build-status "operational"
              :build-type "deployment"}}
   {:tid "s11" :context netops-1 :approval approved
    :note "phase-1 では always-escalate op が phase gate 側の理由で上がる"
    :request {:op :flag-network-fault :site-id "site-001"
              :concern "RFインターフェアランスの可能性"}}

   ;; -- HARD holds: never reach a human ----------------------------------
   {:tid "h01" :context netops-3
    :note "site-002 の spectrum-license-status は :pending"
    :request {:op :activate-tower :site-id "site-002"}}
   {:tid "h02" :context netops-3
    :note "site-003 の site-access-record が未取得"
    :request {:op :provision-subscriber :site-id "site-003"
              :subscriber-ref "sub-778" :service-type "voice-data"}}
   {:tid "h03" :context netops-3
    :note "未登録サイト（store に無い site-id）"
    :request {:op :log-network-build-record :site-id "site-999"
              :equipment-count 2 :build-status "planned"}}
   {:tid "h04" :context netops-3
    :note "設備数 0 は正の観測値ではない"
    :request {:op :log-network-build-record :site-id "site-001"
              :equipment-count 0 :build-status "planned"}}
   {:tid "h05" :context netops-3
    :note "build-status \"AAA+\" はクローズド語彙に無い"
    :request {:op :log-network-build-record :site-id "site-001"
              :equipment-count 3 :build-status "AAA+"}}
   {:tid "h06" :context netops-3
    :note "利用実績エビデンスの引用が空の請求記録"
    :request {:op :log-billing-record :site-id "site-001"
              :usage-evidence []}}
   {:tid "h07" :context netops-3
    :note "基地局設備の直接操作は恒久ブロック"
    :request {:op :operate-tower-equipment :site-id "site-001"}}
   {:tid "h08" :context netops-3
    :note "周波数免許の付与/更新/取消判断の確定も恒久ブロック"
    :request {:op :finalize-spectrum-license-decision :site-id "site-001"}}
   {:tid "h09" :context netops-3
    :note "クローズド allowlist 外の op"
    :request {:op :dispatch-drone-survey :site-id "site-001"}}
   {:tid "h10" :context netops-3 :advisor :rogue
    :note "故障/侵害された advisor が :effect :actuate を主張（rogue advisor を同一 store に注入）"
    :request {:op :log-network-build-record :site-id "site-001"}}])

;; ----------------------------- running the real actor -----------------------------

(defn- exec-step!
  "Run ONE scenario step through the real compiled StateGraph, then --
  only when the graph actually interrupted at `:request-approval` and
  the step supplies a human decision -- resume the SAME thread with it.
  Captures the real `run*` results plus exactly which ledger facts this
  step appended, so the page can distinguish 'held without ever reaching
  a human' from 'a human said no'."
  [st actors {:keys [tid context request approval note advisor]}]
  (let [actor   (get actors (or advisor :mock))
        before  (count (store/ledger st))
        result  (g/run* actor {:request request :context context} {:thread-id tid})
        resumed (when (and approval (= :interrupted (:status result)))
                  (g/run* actor {:approval approval}
                          {:thread-id tid :resume? true}))
        final   (or resumed result)]
    {:tid           tid
     :note          note
     :context       context
     :advisor       (or advisor :mock)
     :request       request
     :approval      approval
     :interrupted?  (= :interrupted (:status result))
     :first-status  (:status result)
     :final-status  (:status final)
     :state         (:state final)
     :resumed-state (:state resumed)
     :facts         (subvec (vec (store/ledger st)) before)}))

(defn run-demo!
  "Seeds a fresh MemStore with this repo's own demo site register,
  compiles the real OperationActor, and drives `scenario` through it.
  Returns `{:store .. :runs [..]}` -- the store is the SSoT the page
  reads its ledger from; the runs carry the per-thread graph results
  (needed to tell a hard hold from a human rejection, and to see where
  an approver's id does or doesn't survive)."
  []
  (let [st     (store/mem-store {:initial-sites (into {} seed-sites)})
        ;; Same store, same checkpointer seam -- ONLY the advisor differs.
        actors {:mock  (operation/build st)
                :rogue (operation/build st {:advisor rogue-advisor})}]
    {:store st
     :runs  (mapv #(exec-step! st actors %) scenario)}))

;; ----------------------------- derivations -----------------------------

(defn hard-holds
  "The HARD governor holds on the ledger: facts the `:hold` node wrote
  from `governor/hold-fact` because the Governor found a non-negotiable
  violation. A human rejection is a DIFFERENT fact (`:approval-rejected`)
  and is deliberately not counted here -- it reached a human."
  [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- hold-rules
  "Distinct governor rule keywords across the given hold facts, sorted."
  [holds]
  (->> holds (mapcat :basis) (remove nil?) distinct (sort-by name) vec))

(defn- run-disposition [run] (get-in run [:state :disposition]))

(defn- run-gate-reason
  "The phase-gate / escalation reason the real run recorded, if any."
  [run]
  (let [audit (get-in run [:state :audit] [])]
    (or (some :phase-reason (filter #(= :governor-hold (:t %)) audit))
        (some :reason (filter #(= :approval-requested (:t %)) audit)))))

(defn- run-outcome
  "Label derived from what the graph actually did, never asserted."
  [run]
  (let [d (run-disposition run)
        i (:interrupted? run)]
    (cond
      (and (= :commit d) i)  [:approved "human-approved commit"]
      (= :commit d)          [:auto "auto-commit"]
      (and (= :hold d) i)    [:rejected "hold after human rejection"]
      (= :hold d)            [:hard "HARD hold (never reached a human)"]
      i                      [:pending "awaiting human approval"]
      :else                  [:other (str d)])))

(defn- run-rules [run]
  (hold-rules (filter #(#{:governor-hold :approval-rejected} (:t %)) (:facts run))))

(defn- contains-key?
  "Does ANY map anywhere inside `form` carry key `k`? Used to derive
  approver retention structurally (by key presence) rather than by
  string-matching an id -- the approver id and the actor id are the same
  string in this scenario, so a value search would give false hits."
  [form k]
  (boolean (some #(and (map? %) (contains? % k))
                 (tree-seq coll? seq form))))

(defn approver-attribution
  "MEASURED, not assumed: after real approvals, where (if anywhere) does
  the approving human's id survive? Walks the actual resumed graph state
  and the actual store ledger. Returns a map of probe -> {:found? :value}
  plus a derived verdict. If this repo is later changed so the ledger
  retains the approver, this page starts saying so on its own."
  [st runs]
  (let [approved-runs (filterv #(and (= :approved (get-in % [:approval :status]))
                                     (:interrupted? %))
                               runs)
        ledger        (vec (store/ledger st))
        sample        (first approved-runs)
        state-val     (get-in sample [:resumed-state :record :payload :approved-by])
        audit-val     (some :by (filter #(= :approval-granted (:t %))
                                        (get-in sample [:resumed-state :audit] [])))
        ledger-key?   (contains-key? ledger :approved-by)
        ledger-by?    (contains-key? ledger :by)]
    {:approvals        (count approved-runs)
     :probes
     [{:where "graph run state — [:record :payload :approved-by]"
       :found? (some? state-val) :value state-val}
      {:where "graph run audit — :approval-granted fact, key :by"
       :found? (some? audit-val) :value audit-val}
      {:where "store ledger — any fact carrying :approved-by (recursive)"
       :found? ledger-key? :value nil}
      {:where "store ledger — any fact carrying :by (recursive)"
       :found? ledger-by? :value nil}]
     :retained-in-ssot? (or ledger-key? ledger-by?)}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- cls [c s] (str "<span class=\"" c "\">" s "</span>"))

(defn- yes-no [b] (if b (cls "ok" "yes") (cls "muted" "no")))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n" (str/join "\n" rows) "\n      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       (when lead (str "    <p class=\"muted\">" lead "</p>\n"))
       body
       "  </section>\n"))

;; ----------------------------- sections -----------------------------

(defn- summary-rows [st runs]
  (let [ledger    (vec (store/ledger st))
        holds     (hard-holds ledger)
        commits   (filterv #(= :committed (:t %)) ledger)
        rejects   (filterv #(= :approval-rejected (:t %)) ledger)
        approved  (filterv #(and (:interrupted? %) (= :commit (run-disposition %))) runs)
        escalated (filterv :interrupted? runs)]
    [(row "シナリオ実行数（1 実行 = 1 StateGraph run）" (cls "num" (count runs)))
     (row "台帳ファクト総数" (cls "num" (count ledger)))
     (row "コミット済みファクト" (cls "num" (count commits)))
     (row "HARD ホールド（人間に到達しない）"
          (cls "critical" (str (count holds))))
     (row "発火した HARD ルール種別"
          (cls "num" (count (hold-rules holds))))
     (row "人間へエスカレーションした実行" (cls "num" (count escalated)))
     (row "人間の承認を経てコミットした実行" (cls "num" (count approved)))
     (row "人間の却下によるホールド" (cls "num" (count rejects)))]))

(defn- site-rows [st ledger]
  (for [[site-id _] seed-sites
        :let [site (store/registered-site st site-id)
              facts (filterv #(= site-id (:subject %)) ledger)
              last-fact (last facts)]]
    (row (code site-id)
         (esc (:name site))
         (let [s (:spectrum-license-status site)]
           (if (= :active s) (cls "ok" (esc (str s))) (cls "warn" (esc (str s)))))
         (yes-no (:site-access-record site))
         (cls "num" (count facts))
         (if last-fact
           (case (:t last-fact)
             :committed         (cls "ok" "committed")
             :governor-hold     (cls "critical" (str "HARD hold · "
                                                     (esc (kw (first (:basis last-fact))))))
             :approval-rejected (cls "warn" "held · approver rejected")
             (cls "muted" (esc (kw (:t last-fact)))))
           (cls "muted" "no activity")))))

(defn- run-rows [runs]
  (for [r runs
        :let [[k label] (run-outcome r)
              rules (run-rules r)]]
    (row (code (:tid r))
         (esc (kw (get-in r [:context :phase])))
         (if (= :rogue (:advisor r))
           (cls "critical" "rogue (注入)")
           (cls "muted" "mock"))
         (code (kw (get-in r [:request :op])))
         (code (get-in r [:request :site-id]))
         (cls (case k :auto "ok" :approved "ok" :rejected "warn"
                      :hard "critical" "muted")
              (esc label))
         (if-let [reason (run-gate-reason r)] (code (kw reason)) (cls "muted" "—"))
         (if (seq rules)
           (str/join "、 " (map #(code (kw %)) rules))
           (cls "muted" "—"))
         (esc (:note r)))))

(defn- hard-hold-rows [runs]
  (for [r runs
        f (:facts r)
        :when (= :governor-hold (:t f))
        v (:violations f)]
    (row (code (:tid r))
         (code (kw (:op f)))
         (code (:subject f))
         (cls "critical" (esc (kw (:rule v))))
         (yes-no (:interrupted? r))
         (esc (:detail v)))))

(defn- op-gate-rows [runs]
  (let [observed (reduce (fn [m r]
                           (update m (get-in r [:request :op])
                                   (fnil conj #{})
                                   (second (run-outcome r))))
                         {} runs)
        all-ops (sort-by name (into governor/all-recognized-ops
                                    (map #(get-in % [:request :op]) runs)))]
    (for [op all-ops]
      (row (code (str op))
           (yes-no (contains? governor/known-ops op))
           (if (contains? governor/blocked-ops op)
             (cls "critical" "permanently blocked") (cls "muted" "—"))
           (if (contains? governor/always-escalate-ops op)
             (cls "warn" "always escalates") (cls "muted" "—"))
           (if-let [o (get observed op)]
             (esc (str/join "、 " (sort o)))
             (cls "muted" "not exercised in this run"))))))

(defn- equipment-rows []
  (for [[id c] (sort-by key facts/equipment-categories)]
    (row (code id) (esc (:name c)) (cls "amt" (esc (:cost-threshold c))))))

(defn- attribution-rows [attr]
  (for [p (:probes attr)]
    (row (esc (:where p))
         (yes-no (:found? p))
         (if (:value p) (code (:value p)) (cls "muted" "—")))))

;; ----------------------------- render -----------------------------

(defn render
  "Pure render of `{:store .. :runs ..}` (as returned by `run-demo!`) to
  a complete HTML document. Deterministic: no clock, no randomness, and
  every collection is either an ordered vector or explicitly sorted."
  [{:keys [store runs]}]
  (let [ledger (vec (store/ledger store))
        holds  (hard-holds ledger)
        rules  (hold-rules holds)
        attr   (approver-attribution store runs)]
    (str
     "<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n"
     "<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<meta name=\"color-scheme\" content=\"light\">\n"
     "<meta name=\"theme-color\" content=\"#ffffff\">\n"
     "<title>Mobile network infrastructure operations (ISIC 6120) — Operator Console</title>\n"
     "<meta name=\"description\" content=\"cloud-itonami-isic-6120 operator console — generated at build time by driving the real governed actor stack.\">\n"
     "<style>" (jp-go-dds.skin/dds+skin) "</style>\n"
     "</head>\n<body>\n"

     "<header class=\"bar\">\n"
     "  <h1>移動体通信インフラ運用コーディネーター（ISIC 6120） — Operator Console</h1>\n"
     "  <p class=\"subtitle\">cloud-itonami-isic-6120 · 読み取り専用サンプル · Mobile Network Governor 監査下</p>\n"
     "  <span class=\"badge\">全ての数値は <code>clojure -M:dev:render-html</code> が実 actor（operation → governor → phase → store）を走らせて得た実行結果</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "この実行の要約"
      (str "このページはビルド時に生成される。<code>wirelesstelecom.render-html/run-demo!</code> が "
           "実際にコンパイル済み StateGraph を " (count runs) " 回走らせ、その結果だけを描画している。"
           "手書きの数値・状態・ID は 1 つも無い。")
      (table ["指標" "値"] (summary-rows store runs)))

     (section
      "サイト登録簿（Store の SSoT）"
      (str "<code>wirelesstelecom.store</code> から読み出したサイト記録。"
           "Governor はサイトが登録済みであること、そして稼働開始・契約者収容の直前に "
           "サイト自身の <code>:spectrum-license-status</code> / <code>:site-access-record</code> を"
           "独立に再検証する（advisor の主張は信用しない）。")
      (table ["site-id" "名称" "spectrum-license-status" "site-access-record"
              "台帳ファクト数" "最新の判定"]
             (site-rows store ledger)))

     (section
      "シナリオ実行（1 行 = 1 StateGraph run）"
      (str "各行は実際の <code>langgraph.graph/run*</code> の戻り値から導出している。"
           "「HARD hold」は graph が <code>:request-approval</code> で一度も割り込まなかった実行 "
           "＝ 人間に到達しなかったホールドを意味する。")
      (table ["thread" "phase" "advisor" "op" "site" "結果" "gate reason" "発火ルール" "説明"]
             (run-rows runs)))

     (section
      (str "HARD ホールド（" (count holds) " 件 / " (count rules) " ルール種別）")
      (str "Governor の hard violation は上書き不可・恒久。下表の「人間に到達」列が全て "
           "<span class=\"muted\">no</span> であることが、これらが人間の判断を経ずに"
           "その場で止まった証拠。detail は Governor が実際に書いた文字列そのもの。")
      (table ["thread" "op" "site" "rule" "人間に到達" "Governor の detail"]
             (hard-hold-rows runs)))

     (section
      "Governor の op 契約"
      (str "allowlist / 恒久ブロック / always-escalate の 3 列は "
           "<code>wirelesstelecom.governor</code> の var をレンダリング時に読んだもの、"
           "最右列はこの実行で実際に観測された結果。信頼度フロア = <code>"
           governor/confidence-floor "</code>。")
      (table ["op" "allowlist 内" "ブロック" "常時エスカレーション" "この実行での観測結果"]
             (op-gate-rows runs)))

     (section
      "参照データ（wirelesstelecom.facts）"
      (str "調達カテゴリごとのエスカレーション閾値。未知カテゴリの既定閾値は <code>"
           facts/default-cost-threshold "</code>。"
           "認識済み build-status: "
           (str/join "、 " (map code (sort facts/build-status-codes)))
           "。site-operation types（検証なしの参考集合）: "
           (str/join "、 " (map code (sort facts/site-operation-types))) "。")
      (table ["category-id" "名称" "cost-threshold"] (equipment-rows)))

     (section
      "承認者の帰属（実測）"
      (str "この repo が承認者 ID をどこまで保持するかは、決めつけず実際に測っている："
           "承認済み実行 " (:approvals attr) " 件の resumed state と、store 台帳全体を"
           "キー存在で走査した結果が下表。"
           (if (:retained-in-ssot? attr)
             " <strong>台帳（SSoT）に承認者 ID が残っている。</strong>"
             (str " <strong>台帳（SSoT）には承認者 ID が残らない。</strong>"
                  " <code>operation/commit-fact</code> は <code>(:value proposal)</code> を "
                  "<code>:record</code> に書くため、<code>:request-approval</code> ノードが "
                  "<code>:payload</code> に載せた <code>:approved-by</code> は台帳に伝播せず、"
                  "<code>:approval-granted</code> ファクト自体も append されない。"
                  "つまり「誰も承認していない」と「承認者を保存していない」は台帳からは区別できない —— "
                  "沈黙させず明示する。これは実装の観測結果であり、この demo commit では修正しない"
                  "（actor の SSoT 挙動の変更になるため）。")))
      (table ["観測点" "承認者 ID が残るか" "実際に見つかった値"] (attribution-rows attr)))

     (section
      "監査台帳（append-only、この実行の全ファクト）"
      (str "<code>wirelesstelecom.store/append-ledger!</code> が追記した順そのまま。"
           "commit は <code>:commit</code> ノード、hold は <code>:hold</code> ノードが書く。")
      (table ["#" "fact" "op" "site" "disposition" "basis"]
             (map-indexed
              (fn [i f]
                (row (cls "num" (inc i))
                     (case (:t f)
                       :committed         (cls "ok" "committed")
                       :governor-hold     (cls "critical" "governor-hold")
                       :approval-rejected (cls "warn" "approval-rejected")
                       (esc (kw (:t f))))
                     (code (kw (:op f)))
                     (code (:subject f))
                     (esc (kw (:disposition f)))
                     (if (seq (:basis f))
                       (str/join "、 " (map #(code (if (keyword? %) (kw %) %)) (:basis f)))
                       (cls "muted" "—"))))
              ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>生成: <code>clojure -M:dev:render-html</code>（<code>src/wirelesstelecom/render_html.clj</code>）。"
     "実 actor 実行の出力のみを描画する決定論的ジェネレータ —— 同じ seed なら常にバイト一致する。"
     "本ページはサンプルであり、実運用の加入者データやサイト機微情報は含まない"
     "（<code>docs/business-model.md</code> Trust Controls）。</p>\n"
     "  <p>"
     (if (some #{:no-execution} rules)
       (str "Governor の HARD ルール <code>:no-execution</code> は、同梱の "
            "<code>wirelesstelecom.advisor/mock-advisor</code> が全分岐で "
            "<code>:effect :propose</code> を返すため通常経路では構造的に到達できない。"
            "この実行では <code>operation/build</code> が公開している <code>:advisor</code> シームから、"
            "<code>:effect :actuate</code>（実世界の作動）を主張する rogue advisor を"
            "<strong>同一 store に注入</strong>して実際に発火させている（thread <code>h10</code>）。"
            "故障・侵害・幻覚した intelligence 層が権限を得られないことを、"
            "散文の主張ではなく実行結果として示すための唯一の誠実な方法である。")
       (str "Governor の HARD ルール <code>:no-execution</code> はこの実行では発火していない："
            "同梱の <code>wirelesstelecom.advisor/mock-advisor</code> が常に <code>:effect :propose</code> を"
            "返すため、mock advisor 経由では構造的に到達できない（実 LLM advisor を挿した時に効くガード）。"))
     "</p>\n"
     "</footer>\n"
     "</body>\n</html>\n")))

(defn -main
  "Regenerate `docs/samples/operator-console.html` from a real run.

  Refuses to write anything when the run produced no HARD governor hold
  -- a console with no real hold cannot be told apart from a mock, and
  shipping one would misrepresent the Governor."
  [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store runs] :as demo} (run-demo!)
        db store
        hs (hard-holds (store/ledger db))]
    (when (empty? hs)
      (throw (ex-info "no governor hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))})))
    (io/make-parents out)
    (spit out (render demo))
    (println "wrote" out
             (str "(" (count runs) " actor runs, "
                  (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds across "
                  (count (hold-rules hs)) " distinct rules: "
                  (str/join ", " (map name (hold-rules hs))) ")"))))
