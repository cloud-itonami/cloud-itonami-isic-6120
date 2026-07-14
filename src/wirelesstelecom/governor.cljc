(ns wirelesstelecom.governor
  "Mobile Network Governor -- the independent compliance layer that
  earns the Network Operations Advisor the right to commit (named
  `:mobile-network-governor` in this repo's own `blueprint.edn`). The
  LLM has no notion of which jurisdiction's spectrum-licensing law is
  official, whether a line's own recorded MSISDN is even syntactically
  valid, whether a line's own subscriber-identity verification has
  actually been completed with full evidence, whether an unresolved
  spectrum-license dispute against the line's site has actually stayed
  unresolved, or when an act stops being a draft and becomes a
  real-world MSISDN provisioning or service suspension, so this MUST
  be a separate system able to *reject* a proposal and fall back to
  HOLD -- the wireless-carrier analog of `cloud-itonami-isic-6190`'s
  own `telecom.governor` (Telecom Access Governor).

  Five checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated spectrum-licensing spec-basis, incomplete evidence, a
  malformed MSISDN, or a double provisioning/suspension). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `wirelesstelecom.phase`: for `:stake :actuation/provision-msisdn`/
  `:actuation/suspend-service` (a real-world act) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the identity proposal cite
                                       an OFFICIAL spectrum-licensing/
                                       subscriber-registration source
                                       (`wirelesstelecom.facts`), or
                                       invent one?
    2. Evidence incomplete         -- for `:actuation/provision-
                                       msisdn`/`:actuation/suspend-
                                       service`, has the line actually
                                       been identity-verified with a
                                       full evidence checklist on file?
    3. MSISDN format invalid       -- for `:actuation/provision-
                                       msisdn`, INDEPENDENTLY recompute
                                       whether the line's own recorded
                                       MSISDN is syntactically valid
                                       (`wirelesstelecom.registry/
                                       msisdn-invalid-format?`) -- needs
                                       no proposal inspection or
                                       stored-verdict lookup at all.
                                       Mirrors `telecom.governor`'s own
                                       `e164-format-invalid` check
                                       (`cloud-itonami-isic-6190`),
                                       applied here to a mobile MSISDN.
    4. Spectrum-license dispute
       unresolved                  -- reported by THIS proposal itself
                                       (a `:license/screen` that just
                                       found an unresolved dispute), or
                                       already on file for the line
                                       (`:license/screen`/
                                       `:actuation/suspend-service`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `telecom.
                                       governor/billing-dispute-
                                       unresolved-violations`
                                       (`cloud-itonami-isic-6190`)
                                       establishes -- applied here to
                                       an unresolved spectrum-license
                                       dispute on the line's site
                                       rather than a billing dispute.
                                       Like its sibling, exercised in
                                       tests/demo via `:license/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened line --
                                       see this ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       provision-msisdn`/`:actuation/
                                       suspend-service` (REAL acts) ->
                                       escalate.

  One more guard pair, double-provisioning/double-suspension
  prevention, is enforced but NOT listed as a numbered HARD check above
  because it needs no upstream comparison at all --
  `already-provisioned-violations`/`already-suspended-violations`
  refuse to provision an MSISDN/suspend service for the SAME line
  twice, off dedicated `:msisdn-provisioned?`/`:service-suspended?`
  facts (never a `:status` value) -- the SAME 'check a dedicated
  boolean, not status' discipline every prior sibling governor's
  guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  Out of scope, by construction, on both independent layers (see
  README `Scope`): lawful-intercept, subscriber-location disclosure
  and law-enforcement-ordered service suspension are NEVER actor
  operations at all -- there is no op in this namespace's `high-stakes`
  set, `wirelesstelecom.phase`'s tables, or `wirelesstelecom.
  opsadvisor`'s `infer` dispatch for any of them, mirroring `telecom.
  governor`'s own sibling posture (`cloud-itonami-isic-6190`'s
  `docs/business-model.md` Trust Controls: 'lawful-intercept and
  emergency paths remain outside LLM control')."
  (:require [wirelesstelecom.facts :as facts]
            [wirelesstelecom.registry :as registry]
            [wirelesstelecom.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Provisioning a real MSISDN and suspending a real subscriber's
  service are the two real-world actuation events this actor
  performs -- a two-member set, matching every prior dual-actuation
  sibling's shape (including `cloud-itonami-isic-6190`'s own
  `telecom.governor/high-stakes`)."
  #{:actuation/provision-msisdn :actuation/suspend-service})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:identity/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  spectrum-licensing/subscriber-registration requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:identity/verify :actuation/provision-msisdn :actuation/suspend-service} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は周波数割当・契約者確認要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/provision-msisdn`/`:actuation/suspend-service`, the
  jurisdiction's required subscriber-identity-verification-record/
  msisdn-assignment-record/spectrum-license-assignment-record/
  service-suspension-log evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/provision-msisdn :actuation/suspend-service} op)
    (let [ln (store/line st subject)
          verification (store/identity-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction ln) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(契約者確認記録/MSISDN割当記録/周波数割当証明/回線停止台帳等)が充足していない状態での提案"}]))))

(defn- msisdn-format-invalid-violations
  "For `:actuation/provision-msisdn`, INDEPENDENTLY recompute whether
  the line's own recorded MSISDN is syntactically valid via
  `wirelesstelecom.registry/msisdn-invalid-format?` -- needs no
  proposal inspection or stored-verdict lookup at all, since its
  inputs are a permanent ground-truth field already on the line."
  [{:keys [op subject]} st]
  (when (= op :actuation/provision-msisdn)
    (let [ln (store/line st subject)]
      (when (registry/msisdn-invalid-format? ln)
        [{:rule :msisdn-format-invalid
          :detail (str subject " の記録番号(" (:msisdn ln) ")はMSISDN(E.164)形式として不正")}]))))

(defn- license-dispute-unresolved-violations
  "An unresolved spectrum-license dispute on the line's site -- reported
  by THIS proposal (e.g. a `:license/screen` that itself just found
  one), or already on file in the store for the line (`:license/
  screen`/`:actuation/suspend-service`) -- is a HARD, un-overridable
  hold. Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding. Mirrors
  `telecom.governor/billing-dispute-unresolved-violations`
  (`cloud-itonami-isic-6190`)."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        line-id (when (contains? #{:license/screen :actuation/suspend-service} op) subject)
        hit-on-file? (and line-id (= :unresolved (:verdict (store/license-screen-of st line-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :license-dispute-unresolved
        :detail "未解決の周波数割当紛争がある状態での回線停止提案は進められない"}])))

(defn- already-provisioned-violations
  "For `:actuation/provision-msisdn`, refuses to provision an MSISDN
  for the SAME line twice, off a dedicated `:msisdn-provisioned?` fact
  (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/provision-msisdn)
    (when (store/line-already-provisioned? st subject)
      [{:rule :already-provisioned
        :detail (str subject " は既にMSISDN発行済み")}])))

(defn- already-suspended-violations
  "For `:actuation/suspend-service`, refuses to suspend service for the
  SAME line twice, off a dedicated `:service-suspended?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/suspend-service)
    (when (store/line-already-suspended? st subject)
      [{:rule :already-suspended
        :detail (str subject " は既に回線停止済み")}])))

(defn check
  "Censors a Network Operations Advisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (msisdn-format-invalid-violations request st)
                           (license-dispute-unresolved-violations request proposal st)
                           (already-provisioned-violations request st)
                           (already-suspended-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
