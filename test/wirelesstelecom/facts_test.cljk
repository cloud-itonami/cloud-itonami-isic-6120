(ns wirelesstelecom.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [wirelesstelecom.facts :as facts]))

(deftest equipment-category-lookup
  (testing "Lookup valid equipment category"
    (let [c (facts/equipment-category-by-id "antenna")]
      (is (= "antenna" (:id c)))
      (is (= "アンテナ" (:name c)))))

  (testing "Lookup invalid equipment category"
    (is (nil? (facts/equipment-category-by-id "unknown")))))

(deftest equipment-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/equipment-category-by-id id)))
      "antenna"             300
      "rf-equipment"        800
      "backhaul-equipment" 2000
      "power-system"       1500)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest build-status-codes-closed-set
  (testing "Recognized build-status codes are present"
    (is (contains? facts/build-status-codes "planned"))
    (is (contains? facts/build-status-codes "in-progress"))
    (is (contains? facts/build-status-codes "commissioned"))
    (is (contains? facts/build-status-codes "operational"))
    (is (contains? facts/build-status-codes "maintenance-hold"))
    (is (contains? facts/build-status-codes "decommissioned")))

  (testing "An unrecognized status code is absent from the closed vocabulary"
    (is (not (contains? facts/build-status-codes "unknown-status")))
    (is (not (contains? facts/build-status-codes "")))))

(deftest build-status-known-predicate
  (testing "Known codes"
    (is (true? (facts/build-status-known? "operational"))))
  (testing "Unknown codes"
    (is (false? (facts/build-status-known? "AAA+")))
    (is (false? (facts/build-status-known? nil)))))

(deftest site-operation-types-reference-set
  (testing "Mobile-network site-operation types are present"
    (is (contains? facts/site-operation-types "deployment"))
    (is (contains? facts/site-operation-types "maintenance"))
    (is (contains? facts/site-operation-types "inspection"))
    (is (contains? facts/site-operation-types "upgrade"))
    (is (contains? facts/site-operation-types "decommissioning")))

  (testing "Not a validated enum -- an unlisted operation type is simply absent"
    (is (not (contains? facts/site-operation-types "retting")))))
