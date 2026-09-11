(ns wirelesstelecom.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [wirelesstelecom.governor :as gov]
            [wirelesstelecom.store :as store]))

(defn- active-site []
  {:id "site-001" :name "North Ridge Tower"
   :spectrum-license-status :active :site-access-record true})

(deftest hard-violations-no-site-id
  (testing "Hard violation: missing site-id"
    (let [req {}
          prop {:op :log-network-build-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :site-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-site
  (testing "Hard violation: site-id present but not registered"
    (let [req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :site-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :execute}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-tower-equipment-blocked
  (testing "Hard violation: direct radio/tower-equipment operation is permanently blocked"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :operate-tower-equipment :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-license-decision-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-spectrum-license-decision-blocked
  (testing "Hard violation: finalizing a spectrum-license decision is permanently blocked"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :finalize-spectrum-license-decision :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-license-decision-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-op-not-allowed
  (testing "Hard violation: op outside the closed allowlist"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :dispatch-drone-survey :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-build-record-invalid
  (testing "Hard violation: non-positive equipment count"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :propose :equipment-count 0 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :build-record-invalid (:rule %)) (:violations verdict))))))

(deftest hard-violations-build-status-invalid
  (testing "Hard violation: unrecognized build-status code"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :propose :equipment-count 3
                :build-status "AAA+" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :build-status-invalid (:rule %)) (:violations verdict))))))

(deftest hard-violations-spectrum-license-not-active
  (testing "Hard violation: activate-tower against a site whose spectrum-license-status isn't :active"
    (let [s (store/mem-store {:initial-sites
                              {"site-002" {:id "site-002" :spectrum-license-status :pending
                                          :site-access-record true}}})
          req {:site-id "site-002"}
          prop {:op :activate-tower :effect :propose :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :spectrum-license-not-active (:rule %)) (:violations verdict)))))

  (testing "OK: activate-tower against a site whose spectrum-license-status IS :active"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :activate-tower :effect :propose :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict))))))

(deftest hard-violations-site-access-record-missing
  (testing "Hard violation: provision-subscriber against a site with no site-access-record on file"
    (let [s (store/mem-store {:initial-sites
                              {"site-003" {:id "site-003" :spectrum-license-status :active
                                          :site-access-record false}}})
          req {:site-id "site-003"}
          prop {:op :provision-subscriber :effect :propose :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :site-access-record-missing (:rule %)) (:violations verdict)))))

  (testing "OK: provision-subscriber against a site WITH an on-file site-access-record"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :provision-subscriber :effect :propose :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict))))))

(deftest hard-violations-usage-evidence-missing
  (testing "Hard violation: billing record with no usage-evidence citation"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-billing-record :effect :propose :usage-evidence [] :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :usage-evidence-missing (:rule %)) (:violations verdict)))))

  (testing "OK: billing record WITH usage-evidence cited"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-billing-record :effect :propose
                :usage-evidence ["meter-reading-2026-07"] :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict))))))

(deftest ok-build-record-logging
  (testing "OK: valid network-build record logging with a registered site"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :propose :equipment-count 3
                :build-status "operational" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest escalation-network-fault
  (testing "Escalation: network fault ALWAYS escalates, even at high confidence"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :flag-network-fault :effect :propose
                :concern "RFインターフェアランスの可能性" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-low-confidence
  (testing "Escalation: confidence below the floor"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :log-network-build-record :effect :propose :equipment-count 3 :confidence 0.5}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-order-equipment-high-cost
  (testing "Escalation: equipment order over the (default) cost threshold"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :order-equipment :effect :propose :cost 600 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-order-equipment-category-specific-threshold
  (testing "Escalation: equipment order over its category-specific threshold (backhaul-equipment: 2000)"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :order-equipment :effect :propose :cost 2200 :confidence 0.9
                :value {:category "backhaul-equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:escalate? verdict))))

  (testing "OK: backhaul-equipment order under its higher category threshold"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :order-equipment :effect :propose :cost 1800 :confidence 0.9
                :value {:category "backhaul-equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-order-equipment-low-cost
  (testing "OK: equipment order under the cost threshold"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :order-equipment :effect :propose :cost 100 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-schedule-site-operation
  (testing "OK: scheduling a site operation is a routine coordination op"
    (let [s (store/mem-store {:initial-sites {"site-001" (active-site)}})
          req {:site-id "site-001"}
          prop {:op :schedule-site-operation :effect :propose :confidence 0.85}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))
