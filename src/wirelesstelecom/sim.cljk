(ns wirelesstelecom.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered site through
  a clean phase-3 auto-commit, an always-escalate network-fault flag
  (human approves), a high-cost equipment order (human rejects), a
  structural spectrum-license hard hold (tower activation against a
  non-active license), a structural site-access hard hold (subscriber
  provisioning without an on-file site-access record), and a hard hold
  (unregistered site), then prints the resulting audit ledger. Mirrors
  `tobaccoops.sim` (cloud-itonami-isic-0115)."
  (:require [langgraph.graph :as g]
            [wirelesstelecom.operation :as operation]
            [wirelesstelecom.store :as store]))

(def operator {:actor-id "netops-01" :role :network-operator :phase :phase-3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "netops-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "netops-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an escalate->reject->hold path, and
  three hard-hold paths (unregistered site, inactive spectrum license,
  missing site-access record); print each result and the final audit
  ledger."
  []
  (let [st (store/mem-store
            {:initial-sites
             {"site-001"
              {:id "site-001"
               :name "North Ridge Tower"
               :spectrum-license-status :active
               :site-access-record true}
              "site-002"
              {:id "site-002"
               :name "Harbor District Tower (license renewal pending)"
               :spectrum-license-status :pending
               :site-access-record true}
              "site-003"
              {:id "site-003"
               :name "West Valley Tower (site-access not yet secured)"
               :spectrum-license-status :active
               :site-access-record false}}})
        actor (operation/build st)]

    (println "=== Mobile Network Infrastructure Operations Coordinator Demo ===")

    (println "\n== log-network-build-record site-001 (phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :log-network-build-record :site-id "site-001"
                       :equipment-count 3 :build-status "operational"
                       :build-type "deployment"}
                      operator))

    (println "\n== flag-network-fault site-001 (ALWAYS escalates -- network operator approves) ==")
    (let [r (exec-op actor "t2"
                     {:op :flag-network-fault :site-id "site-001"
                      :concern "RFインターフェアランスの可能性"}
                     operator)]
      (println r)
      (println "-- network operator approves --")
      (println (approve! actor "t2")))

    (println "\n== order-equipment site-001 over cost threshold (escalates -- operator rejects) ==")
    (let [r (exec-op actor "t3"
                     {:op :order-equipment :site-id "site-001"
                      :category "backhaul-equipment" :cost 2500}
                     operator)]
      (println r)
      (println "-- operator rejects --")
      (println (reject! actor "t3")))

    (println "\n== activate-tower site-002 (spectrum-license-status :pending, not :active -> HARD hold) ==")
    (println (exec-op actor "t4"
                      {:op :activate-tower :site-id "site-002"}
                      operator))

    (println "\n== provision-subscriber site-003 (site-access-record false -> HARD hold) ==")
    (println (exec-op actor "t5"
                      {:op :provision-subscriber :site-id "site-003"
                       :subscriber-ref "sub-778" :service-type "voice-data"}
                      operator))

    (println "\n== activate-tower site-001 (spectrum-license-status :active -> commits) ==")
    (println (exec-op actor "t6"
                      {:op :activate-tower :site-id "site-001"}
                      operator))

    (println "\n== log-network-build-record site-999 (unregistered -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t7"
                      {:op :log-network-build-record :site-id "site-999"
                       :equipment-count 2 :build-status "planned"}
                      operator))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
