(ns wirelesstelecom.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean line through
  intake -> identity verification -> spectrum-license-dispute
  screening -> MSISDN-provisioning proposal (always escalates) ->
  human approval -> commit, then through service-suspension proposal
  (always escalates) -> human approval -> commit, then shows five HARD
  holds (a jurisdiction with no spec-basis, a malformed MSISDN, an
  unresolved spectrum-license dispute screened directly via
  `:license/screen` [never via an actuation op against an unscreened
  line -- see this actor's own governor ns docstring / the lesson
  `telecom.governor`'s own ns docstring (`cloud-itonami-isic-6190`)
  records], and a double MSISDN-provisioning/service-suspension of an
  already-processed line) that never reach a human at all, and prints
  the audit ledger + the draft MSISDN-provisioning and
  service-suspension records."
  (:require [langgraph.graph :as g]
            [wirelesstelecom.store :as store]
            [wirelesstelecom.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :network-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== line/intake line-1 (JPN, clean; valid MSISDN, no license dispute) ==")
    (println (exec! actor "t1" {:op :line/intake :subject "line-1"
                                :patch {:id "line-1" :holder-name "Sakura Mobile Co-op"}} operator))

    (println "== identity/verify line-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :identity/verify :subject "line-1"} operator))
    (println (approve! actor "t2"))

    (println "== license/screen line-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :license/screen :subject "line-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/provision-msisdn line-1 (always escalates -- actuation/provision-msisdn) ==")
    (let [r (exec! actor "t4" {:op :actuation/provision-msisdn :subject "line-1"} operator)]
      (println r)
      (println "-- human network operator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/suspend-service line-1 (always escalates -- actuation/suspend-service) ==")
    (let [r (exec! actor "t5" {:op :actuation/suspend-service :subject "line-1"} operator)]
      (println r)
      (println "-- human network operator approves --")
      (println (approve! actor "t5")))

    (println "== identity/verify line-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :identity/verify :subject "line-2" :no-spec? true} operator))

    (println "== identity/verify line-3 (escalates -- human approves; sets up the malformed-MSISDN test) ==")
    (println (exec! actor "t7" {:op :identity/verify :subject "line-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/provision-msisdn line-3 (\"0312345678\" is not valid MSISDN/E.164 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/provision-msisdn :subject "line-3"} operator))

    (println "== license/screen line-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :license/screen :subject "line-4"} operator))

    (println "== actuation/provision-msisdn line-1 AGAIN (double-provisioning -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/provision-msisdn :subject "line-1"} operator))

    (println "== actuation/suspend-service line-1 AGAIN (double-suspension -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/suspend-service :subject "line-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft MSISDN-provisioning records ==")
    (doseq [r (store/provisioning-history db)] (println r))

    (println "== draft service-suspension records ==")
    (doseq [r (store/suspension-history db)] (println r))))
