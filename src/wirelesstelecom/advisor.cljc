(ns wirelesstelecom.advisor
  "Network Operations Advisor -- the contained LLM/decision node (named
  in this repo's own README `Core Contract` diagram). This actor's
  intelligence layer proposes back-office coordination actions
  (network-build/maintenance record logging, deployment/maintenance/
  inspection scheduling, tower-activation requests, subscriber-
  provisioning requests, billing/usage record logging, network-fault
  flags, equipment procurement) based on site state and operator input.
  The advisor is SEALED into the `:advise` step of the operation flow;
  every proposal is routed through the independent Governor before
  committing.

  The advisor makes proposals but has NO direct authority. Proposals
  are always censored by:
    1. Governor (site registration, closed-op allowlist,
       spectrum-license/site-access/cost/network-fault gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production
  should use langchain/Claude or similar LLM backend (same seam point
  as `tobaccoops.advisor`, cloud-itonami-isic-0115)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g. :equipment-count/
    :build-status/:usage-evidence/:cost)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op site-id]} request]
      (case op
        :log-network-build-record
        {:op :log-network-build-record
         :effect :propose
         :equipment-count (:equipment-count request 0)
         :build-status (:build-status request "planned")
         :value {:site-id site-id
                 :equipment-count (:equipment-count request 0)
                 :build-status (:build-status request "planned")
                 :build-type (:build-type request "deployment")}
         :cites ["operator-submitted-site-data"]
         :summary "Tower/base-station deployment or maintenance build record logged from operator submission"
         :confidence 0.9}

        :schedule-site-operation
        {:op :schedule-site-operation
         :effect :propose
         :value {:site-id site-id
                 :operation-type (:operation-type request "deployment")
                 :requested-date (:requested-date request)
                 :reason (:reason request "routine-schedule")}
         :cites ["operator-scheduling-request"]
         :summary "Site operation (deployment/maintenance/inspection) proposed per operator request"
         :confidence 0.85}

        :activate-tower
        {:op :activate-tower
         :effect :propose
         :value {:site-id site-id}
         :cites ["operator-activation-request"]
         :summary "Tower/base-station activation proposed pending independent spectrum-license verification"
         :confidence 0.85}

        :provision-subscriber
        {:op :provision-subscriber
         :effect :propose
         :value {:site-id site-id
                 :subscriber-ref (:subscriber-ref request "unspecified")
                 :service-type (:service-type request "voice-data")}
         :cites ["operator-provisioning-request"]
         :summary "Subscriber provisioning proposed pending independent site-access verification"
         :confidence 0.85}

        :log-billing-record
        {:op :log-billing-record
         :effect :propose
         :usage-evidence (:usage-evidence request [])
         :value {:site-id site-id
                 :amount (:amount request 0)
                 :usage-evidence (:usage-evidence request [])}
         :cites ["operator-submitted-usage-data"]
         :summary "Billing/usage record proposed from operator-submitted usage evidence"
         :confidence 0.85}

        :flag-network-fault
        {:op :flag-network-fault
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:site-id site-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "network-operator-review"}
         :cites ["operator-observation"]
         :summary "Network fault or safety-critical site-work concern (equipment failure, interference, out-of-band operation, tower-climb/high-voltage work) flagged for network-operator review"
         :confidence 0.8}

        :order-equipment
        {:op :order-equipment
         :effect :propose
         :cost (:cost request 0)
         :value {:site-id site-id
                 :category (:category request "antenna")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Equipment order (antenna/RF/backhaul/power-system) proposed for site"
         :confidence 0.85}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a
  proposal is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :site-id (:site-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
