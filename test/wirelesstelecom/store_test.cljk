(ns wirelesstelecom.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [wirelesstelecom.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial sites"
    (let [sites {"site-001" {:id "site-001" :name "North Ridge Tower"}}
          st (store/mem-store {:initial-sites sites})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-site-retrieval
  (testing "Retrieve existing site"
    (let [site {:id "site-001" :name "North Ridge Tower"}
          st (store/mem-store {:initial-sites {"site-001" site}})]
      (is (= site (store/registered-site st "site-001")))))

  (testing "Retrieve non-existent site"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-site st "no-such-site")))))

  (testing "nil site-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-sites {"site-001" {:id "site-001"}}})]
      (is (nil? (store/registered-site st nil))))))

(deftest add-site-test
  (testing "Register a new site"
    (let [st (store/mem-store)
          site-data {:id "site-002" :name "New Tower"}
          result (store/add-site st "site-002" site-data)]
      (is (= site-data result))
      (is (= site-data (store/registered-site st "site-002")))))

  (testing "Update an existing site (e.g. spectrum-license renewal)"
    (let [st (store/mem-store {:initial-sites {"site-001" {:id "site-001"
                                                            :spectrum-license-status :pending}}})
          updated {:id "site-001" :spectrum-license-status :active}
          result (store/add-site st "site-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-site st "site-001"))))))

(deftest ledger-append-only
  (testing "Ledger starts empty and accumulates in append order"
    (let [st (store/mem-store)]
      (is (empty? (store/ledger st)))
      (store/append-ledger! st {:t :committed :op :log-network-build-record})
      (store/append-ledger! st {:t :governor-hold :op :activate-tower})
      (is (= 2 (count (store/ledger st))))
      (is (= [:committed :governor-hold] (mapv :t (store/ledger st)))))))
