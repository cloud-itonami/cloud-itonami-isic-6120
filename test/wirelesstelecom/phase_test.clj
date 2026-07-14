(ns wirelesstelecom.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/provision-msisdn`/`:actuation/suspend-
  service` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [wirelesstelecom.phase :as phase]))

(deftest provision-msisdn-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real MSISDN provisioning"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/provision-msisdn))
          (str "phase " n " must not auto-commit :actuation/provision-msisdn")))))

(deftest suspend-service-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real service suspension"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/suspend-service))
          (str "phase " n " must not auto-commit :actuation/suspend-service")))))

(deftest license-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :license/screen))
          (str "phase " n " must not auto-commit :license/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":line/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:line/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :line/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/provision-msisdn} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/suspend-service} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :line/intake} :commit)))))
