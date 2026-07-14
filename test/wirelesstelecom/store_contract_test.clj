(ns wirelesstelecom.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6190`'s
  `telecom.store-contract-test` for the same pattern on the sibling
  wired-telecom actor."
  (:require [clojure.test :refer [deftest is testing]]
            [wirelesstelecom.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Mobile Co-op" (:holder-name (store/line s "line-1"))))
      (is (= "JPN" (:jurisdiction (store/line s "line-1"))))
      (is (= "+819012345678" (:msisdn (store/line s "line-1"))))
      (is (false? (:license-dispute-unresolved? (store/line s "line-1"))))
      (is (= "0312345678" (:msisdn (store/line s "line-3"))))
      (is (true? (:license-dispute-unresolved? (store/line s "line-4"))))
      (is (false? (:msisdn-provisioned? (store/line s "line-1"))))
      (is (false? (:service-suspended? (store/line s "line-1"))))
      (is (= ["line-1" "line-2" "line-3" "line-4"]
             (mapv :id (store/all-lines s))))
      (is (nil? (store/license-screen-of s "line-1")))
      (is (nil? (store/identity-verification-of s "line-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/provisioning-history s)))
      (is (= [] (store/suspension-history s)))
      (is (zero? (store/next-provisioning-sequence s "JPN")))
      (is (zero? (store/next-suspension-sequence s "JPN")))
      (is (false? (store/line-already-provisioned? s "line-1")))
      (is (false? (store/line-already-suspended? s "line-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :line/upsert
                                 :value {:id "line-1" :holder-name "Sakura Mobile Co-op"}})
        (is (= "Sakura Mobile Co-op" (:holder-name (store/line s "line-1"))))
        (is (= "+819012345678" (:msisdn (store/line s "line-1"))) "unrelated field preserved"))
      (testing "verification / license-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["line-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/identity-verification-of s "line-1")))
        (store/commit-record! s {:effect :license-screen/set :path ["line-1"]
                                 :payload {:line-id "line-1" :verdict :resolved}})
        (is (= {:line-id "line-1" :verdict :resolved} (store/license-screen-of s "line-1"))))
      (testing "MSISDN provisioning drafts a record and advances the sequence"
        (store/commit-record! s {:effect :line/mark-provisioned :path ["line-1"]})
        (is (= "JPN-MSISDN-000000" (get (first (store/provisioning-history s)) "record_id")))
        (is (= "msisdn-provisioning-draft" (get (first (store/provisioning-history s)) "kind")))
        (is (true? (:msisdn-provisioned? (store/line s "line-1"))))
        (is (= 1 (count (store/provisioning-history s))))
        (is (= 1 (store/next-provisioning-sequence s "JPN")))
        (is (true? (store/line-already-provisioned? s "line-1")))
        (is (false? (store/line-already-provisioned? s "line-2"))))
      (testing "service suspension drafts a record and advances the sequence"
        (store/commit-record! s {:effect :line/mark-suspended :path ["line-1"]})
        (is (= "JPN-SUS-000000" (get (first (store/suspension-history s)) "record_id")))
        (is (= "service-suspension-draft" (get (first (store/suspension-history s)) "kind")))
        (is (true? (:service-suspended? (store/line s "line-1"))))
        (is (= 1 (count (store/suspension-history s))))
        (is (= 1 (store/next-suspension-sequence s "JPN")))
        (is (true? (store/line-already-suspended? s "line-1")))
        (is (false? (store/line-already-suspended? s "line-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/line s "nope")))
    (is (= [] (store/all-lines s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/provisioning-history s)))
    (is (= [] (store/suspension-history s)))
    (is (zero? (store/next-provisioning-sequence s "JPN")))
    (is (zero? (store/next-suspension-sequence s "JPN")))
    (store/with-lines s {"x" {:id "x" :holder-name "n" :msisdn "+819012345678"
                             :license-dispute-unresolved? false
                             :msisdn-provisioned? false :service-suspended? false
                             :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:holder-name (store/line s "x"))))))
