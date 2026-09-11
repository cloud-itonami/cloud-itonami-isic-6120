(ns wirelesstelecom.governor
  "Mobile Network Governor -- the independent compliance layer that
  earns the Network Operations Advisor the right to commit (named
  `:mobile-network-governor` in this repo's own `blueprint.edn`). The
  LLM has no notion of:
    - Whether the site (cell tower / base station) a proposal targets
      is actually registered
    - Whether a proposal is a real actuation (`:effect :propose` only --
      this actor NEVER directly operates radio/tower equipment or
      finalizes a spectrum-license grant/renewal/revocation decision)
    - Whether an op is inside this actor's closed coordination allowlist
    - Whether a logged network-build record's equipment count is a
      plausible positive observation
    - Whether a logged network-build record's build-status is a
      recognized status code
    - Whether a site's OWN recorded spectrum-license-status is actually
      `:active` before a tower activation may even be proposed to commit
    - Whether a site's OWN recorded site-access-record is actually on
      file before a subscriber-provisioning proposal may commit
    - Whether a billing/usage record actually cites usage evidence
    - Whether an order-equipment proposal's cost exceeds its category's
      escalation threshold

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor is a back-office NETWORK OPERATIONS COORDINATOR only --
  direct radio/tower-equipment operation and finalizing a spectrum-
  license grant/renewal/revocation decision are categorically outside
  its authority (site engineer / spectrum-license holder exclusive).
  The Governor enforces that boundary structurally, not by trusting the
  advisor's judgment.

  CRITICAL: Any proposal to flag a network fault (equipment failure,
  interference, structural/tower-integrity concern, or a request to
  perform safety-critical site work -- operating outside a licensed
  spectrum band, tower-climb, or high-voltage work, per this repo's own
  README `Robotics premise` paragraph) ALWAYS escalates to a human
  network operator for final sign-off. The LLM's confidence is never
  sufficient for network-safety decisions.

  Hard violations (always HOLD, no override, permanent):
    1. Site not registered (site-id missing or unknown to Store)
    2. Proposal `:effect` is not `:propose` (no direct execution, ever)
    3. Op is `:operate-tower-equipment` or
       `:finalize-spectrum-license-decision` -- direct radio/tower-
       equipment operation and finalizing a spectrum-license grant/
       renewal/revocation decision are PERMANENTLY blocked regardless
       of proposal content or confidence
    4. Op is outside the closed proposal-op allowlist
    5. `:log-network-build-record` with a non-positive equipment count
    6. `:log-network-build-record` with an unrecognized build-status
       code
    7. `:activate-tower` when the site's OWN recorded
       `:spectrum-license-status` is not `:active` -- STRUCTURAL only
       (see `wirelesstelecom.facts` docstring / `docs/adr/
       0002-site-spectrum-scope-correction.md`): this check never cites
       any specific jurisdiction's licensing regime, it only verifies
       the site's own on-file status literally equals `:active`
    8. `:provision-subscriber` when the site's OWN recorded
       `:site-access-record` is not present (nil/false)

  Soft gates (always escalate for human):
    - `:flag-network-fault` -- ALWAYS escalates
    - `:order-equipment` above its category cost threshold
    - `:log-billing-record` with missing usage-evidence -- HARD, not
      soft (see check 9 below); listed here only for contrast
    - Low confidence

  9. `:log-billing-record` with no usage-evidence citation -- HARD (see
     `docs/business-model.md` Trust Control 'billing records require
     verified usage evidence')

  This design mirrors `tobaccoops.governor` (cloud-itonami-isic-0115)
  but specializes mobile-network back-office coordination concerns
  (site registration, closed op allowlist, radio/tower-equipment-
  operation and spectrum-license-decision exclusion, structural
  spectrum-license-active gate, structural site-access-record gate,
  cost threshold, build-status vocabulary, usage-evidence requirement)
  rather than tobacco-farm-growing concerns."
  (:require [wirelesstelecom.facts :as facts]
            [wirelesstelecom.registry :as registry]
            [wirelesstelecom.store :as store]))

(def confidence-floor 0.7)

(def blocked-ops
  "Direct radio/tower-equipment operation and finalizing a spectrum-
  license grant/renewal/revocation decision sit outside this actor's
  coordination-only authority. ALWAYS a hard, permanent block -- never
  escalate, never override, regardless of confidence or cites."
  #{:operate-tower-equipment :finalize-spectrum-license-decision})

(def known-ops
  "The closed allowlist of proposal ops this actor may make -- all
  `:effect :propose` (see `docs/adr/0002-site-spectrum-scope-
  correction.md`)."
  #{:log-network-build-record :schedule-site-operation :activate-tower
    :provision-subscriber :log-billing-record :flag-network-fault
    :order-equipment})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off even when the Governor finds
  no hard violation and confidence is high. Flagging a network fault is
  never something this actor resolves autonomously."
  #{:flag-network-fault})

(def all-recognized-ops
  "known-ops (allowed to proceed) union blocked-ops (recognized but
  permanently forbidden). Anything outside this union is an unknown op
  -- a HARD violation, not a silent no-op."
  (into known-ops blocked-ops))

;; ----------------------------- checks -----------------------------

(defn- site-violations
  "A proposal referencing an unregistered (or absent) site-id is a HARD
  violation -- never act on behalf of a site this actor cannot
  independently verify."
  [{:keys [site-id]} st]
  (when-not (store/registered-site st site-id)
    [{:rule :site-not-registered
      :detail (str "site-id " (pr-str site-id) " は登録済みサイトとして確認できない -- サイト登録前の提案は進められない")}]))

(defn- execution-violations
  "This actor never executes directly. Any proposal whose `:effect`
  isn't `:propose` is a HARD violation, independent of what op it
  claims."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :no-execution
      :detail "提案の :effect は :propose でなければならない -- governor は直接実行/作動を許可しない"}]))

(defn- blocked-op-violations
  "Direct radio/tower-equipment operation and finalizing a spectrum-
  license grant/renewal/revocation decision are a HARD, permanent
  block -- equipment-operation and licensing-decision authority remains
  exclusively human (site engineer / licensee)."
  [proposal]
  (when (contains? blocked-ops (:op proposal))
    [{:rule :equipment-or-license-decision-blocked
      :detail (str (:op proposal) " は基地局設備の直接操作、または周波数免許の付与/更新/取消判断の確定であり、恒久的にブロックされる -- サイト技術者/免許保有者の専権事項")}]))

(defn- unknown-op-violations
  "Enforce the closed proposal-op allowlist independently of the
  advisor's claim -- an op outside `all-recognized-ops` is a HARD
  violation, never a silent pass-through."
  [proposal]
  (when-not (contains? all-recognized-ops (:op proposal))
    [{:rule :op-not-allowed
      :detail (str (:op proposal) " はクローズドallowlist外の操作")}]))

(defn- build-record-invalid-violations
  "For `:log-network-build-record`, INDEPENDENTLY verify the logged
  equipment count is a plausible positive observation via
  `registry/equipment-count-non-positive?`. Evaluated only when an
  `:equipment-count` is present on the proposal."
  [proposal]
  (when (and (= :log-network-build-record (:op proposal))
             (contains? proposal :equipment-count)
             (registry/equipment-count-non-positive? (:equipment-count proposal)))
    [{:rule :build-record-invalid
      :detail (str "設備数 " (:equipment-count proposal) " は正の数でなければならない -- 建設記録の提案は進められない")}]))

(defn- build-status-invalid-violations
  "For `:log-network-build-record`, INDEPENDENTLY verify a logged
  build-status is one of the actor's recognized closed vocabulary
  (`wirelesstelecom.facts/build-status-codes`) via
  `registry/build-status-unknown?`. Evaluated only when a
  `:build-status` is present on the proposal."
  [proposal]
  (when (and (= :log-network-build-record (:op proposal))
             (contains? proposal :build-status)
             (registry/build-status-unknown? (:build-status proposal)))
    [{:rule :build-status-invalid
      :detail (str "建設ステータス（build-status） " (pr-str (:build-status proposal)) " は認識済みのステータスコードではない -- 建設記録の提案は進められない")}]))

(defn- spectrum-license-not-active-violations
  "For `:activate-tower`, INDEPENDENTLY re-verify the site's OWN
  recorded `:spectrum-license-status` literally equals `:active` --
  never trust the advisor's own claim about license status. Skipped
  when the site itself is unregistered (`site-violations` already HARD-
  holds that case). STRUCTURAL ONLY: this never cites a jurisdiction's
  licensing regime, only the site's own on-file status (see
  `wirelesstelecom.facts` docstring)."
  [{:keys [site-id]} proposal st]
  (when (= :activate-tower (:op proposal))
    (let [site (store/registered-site st site-id)]
      (when (and site (not= :active (:spectrum-license-status site)))
        [{:rule :spectrum-license-not-active
          :detail (str "site-id " (pr-str site-id) " の spectrum-license-status は "
                       (pr-str (:spectrum-license-status site))
                       " -- :active でない限り基地局の稼働開始は進められない")}]))))

(defn- site-access-record-missing-violations
  "For `:provision-subscriber`, INDEPENDENTLY re-verify the site's OWN
  recorded `:site-access-record` is present -- never trust the
  advisor's own claim that site access has been secured. Skipped when
  the site itself is unregistered (`site-violations` already
  HARD-holds that case)."
  [{:keys [site-id]} proposal st]
  (when (= :provision-subscriber (:op proposal))
    (let [site (store/registered-site st site-id)]
      (when (and site (not (:site-access-record site)))
        [{:rule :site-access-record-missing
          :detail (str "site-id " (pr-str site-id) " に site-access-record が確認できない -- サイトアクセス権未確認の状態での契約者収容は進められない")}]))))

(defn- usage-evidence-missing-violations
  "For `:log-billing-record`, INDEPENDENTLY verify usage-evidence was
  actually cited via `registry/usage-evidence-missing?` -- do not trust
  the advisor's self-reported confidence alone."
  [proposal]
  (when (= :log-billing-record (:op proposal))
    (when (registry/usage-evidence-missing? (:usage-evidence proposal))
      [{:rule :usage-evidence-missing
        :detail "利用実績エビデンス（usage-evidence）の引用が無い請求記録の提案は進められない"}])))

(defn- cost-threshold-for
  "Resolve the escalation threshold for an order-equipment proposal:
  the category-specific threshold from `wirelesstelecom.facts` if the
  category is known, else the conservative default."
  [proposal]
  (let [category (get-in proposal [:value :category])
        c (and category (facts/equipment-category-by-id category))]
    (or (:cost-threshold c) facts/default-cost-threshold)))

(defn check
  "Censors a Network Operations Advisor proposal against the Governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (site-violations request st)
                           (execution-violations proposal)
                           (blocked-op-violations proposal)
                           (unknown-op-violations proposal)
                           (build-record-invalid-violations proposal)
                           (build-status-invalid-violations proposal)
                           (spectrum-license-not-active-violations request proposal st)
                           (site-access-record-missing-violations request proposal st)
                           (usage-evidence-missing-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (registry/confidence-below-floor? conf confidence-floor)
        cost (:cost proposal)
        high-cost? (boolean (and cost (registry/cost-exceeds-threshold?
                                        cost (cost-threshold-for proposal))))
        always-escalate? (contains? always-escalate-ops (:op proposal))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not high-cost?) (not always-escalate?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? high-cost? always-escalate?))
     :high-stakes? (boolean (or high-cost? always-escalate?))}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
