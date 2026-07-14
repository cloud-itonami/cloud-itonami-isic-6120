(ns wirelesstelecom.registry
  "Pure-function MSISDN-provisioning + service-suspension record
  construction -- an append-only mobile-network-operator book-of-record
  draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a provisioning or suspension reference
  number -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `wirelesstelecom.facts` uses.

  `msisdn-invalid-format?` mirrors `telecom.registry/e164-invalid-
  format?` (`cloud-itonami-isic-6190`, the sibling wired-telecom
  actor) -- a mobile subscriber's MSISDN IS itself an E.164-formatted
  number, so this is the SAME simplified structural check (leading
  `+`, no leading zero, 8-15 total digits), applied here to a real
  mobile subscriber line rather than a fixed-line/VoIP one. Not a full
  ITU-T E.164 numbering-plan validator (see `wirelesstelecom.facts`'s
  docstring for the same honest-scope discipline) -- but a genuine
  ground-truth recompute on the line's own recorded MSISDN,
  independent of any advisor self-report.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real HLR/HSS/radio access network. It builds the RECORD
  an operator would keep, not the act of provisioning the MSISDN or
  suspending the service itself (that is `wirelesstelecom.operation`'s
  `:actuation/provision-msisdn`/`:actuation/suspend-service`, always
  human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  operator's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn msisdn-invalid-format?
  "Is `line`'s own recorded `:msisdn` NOT a syntactically valid E.164
  mobile subscriber number -- a leading `+`, no leading zero after it,
  and 8-15 total digits? A pure ground-truth check against the line's
  own permanent field -- no upstream comparison needed. Mirrors
  `telecom.registry/e164-invalid-format?` (`cloud-itonami-isic-6190`),
  applied here to a mobile MSISDN."
  [{:keys [msisdn]}]
  (or (nil? msisdn)
      (not (re-matches #"\+[1-9]\d{7,14}" msisdn))))

(defn register-msisdn-provisioning
  "Validate + construct the MSISDN-PROVISIONING registration DRAFT --
  the operator's own act of activating a real mobile subscriber
  number (MSISDN) + SIM for a line. Pure function -- does not touch
  any real HLR/HSS; it builds the RECORD an operator would keep.
  `wirelesstelecom.governor` independently re-verifies the line's own
  MSISDN format validity and identity-verification sufficiency, and
  blocks a double-provisioning for the same line, before this is ever
  allowed to commit."
  [line-id jurisdiction sequence]
  (when-not (and line-id (not= line-id ""))
    (throw (ex-info "msisdn-provisioning: line_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "msisdn-provisioning: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "msisdn-provisioning: sequence must be >= 0" {})))
  (let [provisioning-number (str (str/upper-case jurisdiction) "-MSISDN-" (zero-pad sequence 6))
        record {"record_id" provisioning-number
                "kind" "msisdn-provisioning-draft"
                "line_id" line-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "provisioning_number" provisioning-number
     "certificate" (unsigned-certificate "MsisdnProvisioning" provisioning-number provisioning-number)}))

(defn register-service-suspension
  "Validate + construct the SERVICE-SUSPENSION registration DRAFT --
  the operator's own act of suspending a real subscriber's wireless
  service (e.g. for non-payment). Pure function -- does not touch any
  real HLR/HSS or radio access network; it builds the RECORD an
  operator would keep. `wirelesstelecom.governor` independently
  re-verifies the line's own evidence sufficiency, and blocks a
  double-suspension for the same line, before this is ever allowed to
  commit. Like `telecom.registry/register-billing-suppression`
  (`cloud-itonami-isic-6190`), this actuation is a NEGATIVE act
  (withholding ongoing connectivity rather than issuing a new one) --
  see README `Actuation` and this actor's own `docs/adr/
  0001-architecture.md` Decision 1 for the honest framing this makes."
  [line-id jurisdiction sequence]
  (when-not (and line-id (not= line-id ""))
    (throw (ex-info "service-suspension: line_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "service-suspension: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "service-suspension: sequence must be >= 0" {})))
  (let [suspension-number (str (str/upper-case jurisdiction) "-SUS-" (zero-pad sequence 6))
        record {"record_id" suspension-number
                "kind" "service-suspension-draft"
                "line_id" line-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "suspension_number" suspension-number
     "certificate" (unsigned-certificate "ServiceSuspension" suspension-number suspension-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
