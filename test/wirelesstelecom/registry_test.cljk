(ns wirelesstelecom.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [wirelesstelecom.registry :as registry]))

(deftest cost-exceeds-threshold-test
  (testing "Cost within threshold"
    (is (false? (registry/cost-exceeds-threshold? 400 500))))

  (testing "Cost at threshold (inclusive boundary, not exceeded)"
    (is (false? (registry/cost-exceeds-threshold? 500 500))))

  (testing "Cost exceeds threshold"
    (is (true? (registry/cost-exceeds-threshold? 600 500)))))

(deftest equipment-count-non-positive-test
  (testing "Positive equipment count is valid"
    (is (false? (registry/equipment-count-non-positive? 3))))

  (testing "Zero equipment count is invalid"
    (is (true? (registry/equipment-count-non-positive? 0))))

  (testing "Negative equipment count is invalid"
    (is (true? (registry/equipment-count-non-positive? -1)))))

(deftest build-status-unknown-test
  (testing "Recognized status codes are known"
    (is (false? (registry/build-status-unknown? "planned")))
    (is (false? (registry/build-status-unknown? "operational"))))

  (testing "An unrecognized status code is unknown"
    (is (true? (registry/build-status-unknown? "AAA+"))))

  (testing "nil status is unknown"
    (is (true? (registry/build-status-unknown? nil)))))

(deftest confidence-below-floor-test
  (testing "Confidence above floor"
    (is (false? (registry/confidence-below-floor? 0.9 0.7))))

  (testing "Confidence at floor (inclusive, not below)"
    (is (false? (registry/confidence-below-floor? 0.7 0.7))))

  (testing "Confidence below floor"
    (is (true? (registry/confidence-below-floor? 0.5 0.7)))))

(deftest usage-evidence-missing-test
  (testing "Non-empty evidence is present"
    (is (false? (registry/usage-evidence-missing? ["meter-reading-2026-07"]))))

  (testing "Empty coll is missing"
    (is (true? (registry/usage-evidence-missing? []))))

  (testing "nil is missing"
    (is (true? (registry/usage-evidence-missing? nil)))))
