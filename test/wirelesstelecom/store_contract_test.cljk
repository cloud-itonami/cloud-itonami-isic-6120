(ns wirelesstelecom.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `tobaccoops.store-contract-test` (cloud-itonami-isic-0115)."
  (:require [clojure.test :refer [deftest is]]
            [wirelesstelecom.store :as store]))

(defn- exercise [s]
  (store/add-site s "site-x" {:id "site-x" :name "X Tower"
                              :spectrum-license-status :pending
                              :site-access-record false})
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:site/id is :db.unique/identity) the same way
  ;; MemStore's plain `assoc` re-registration does -- e.g. a spectrum-
  ;; license renewal flipping :pending -> :active.
  (store/add-site s "site-x" {:id "site-x" :name "X Tower"
                              :spectrum-license-status :active
                              :site-access-record true})
  (store/append-ledger! s {:t :committed :op :log-network-build-record :subject "site-x"})
  (store/append-ledger! s {:t :approval-requested :op :flag-network-fault :subject "site-x"})
  {:site   (store/registered-site s "site-x")
   :absent (store/registered-site s "no-such-site")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:site m) (:site d)))
    (is (:site-access-record (:site m)) "re-registration upserts, not forks history")
    (is (= :active (:spectrum-license-status (:site m))))
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))

(deftest datomic-store-seeded-sites
  (let [dat (store/datomic-store {:initial-sites
                                   {"site-y" {:id "site-y" :name "Y Tower"}}})]
    (is (= {:id "site-y" :name "Y Tower"} (store/registered-site dat "site-y")))
    (is (empty? (store/ledger dat)))))
