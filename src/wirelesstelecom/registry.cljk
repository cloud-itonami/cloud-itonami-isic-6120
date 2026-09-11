(ns wirelesstelecom.registry
  "Pure validation functions for mobile-network-operator infrastructure
  coordination. These are called by the Governor to independently
  verify proposal parameters -- the LLM advisor's confidence is NOT
  sufficient to override these checks. Mirrors `tobaccoops.registry`
  (cloud-itonami-isic-0115) in shape, adding
  `usage-evidence-missing?` (this domain's own billing-integrity
  measure: a billing/usage record must cite the usage evidence it was
  drawn from, matching `docs/business-model.md`'s Trust Control
  'billing records require verified usage evidence')."
  (:require [wirelesstelecom.facts :as facts]))

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn equipment-count-non-positive?
  "A logged network-build record's equipment count of zero or negative
  is not a real observation -- reject it as a HARD violation rather
  than silently accepting bad data into the build record."
  [equipment-count]
  (<= equipment-count 0))

(defn build-status-unknown?
  "A logged build-status that isn't in the actor's recognized closed
  vocabulary (`wirelesstelecom.facts/build-status-codes`) is not a
  plausible observation -- reject it as a HARD violation (an
  independent structural plausibility check on a domain-specific
  field, not a judgment about the deployment's actual condition)."
  [status]
  (not (facts/build-status-known? status)))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))

(defn usage-evidence-missing?
  "A billing/usage record with no usage-evidence citation is not
  verifiable -- structural (is there ANY evidence cited at all), not a
  judgment about whether the usage amount itself is correct."
  [usage-evidence]
  (empty? usage-evidence))
