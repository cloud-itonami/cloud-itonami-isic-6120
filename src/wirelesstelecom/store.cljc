(ns wirelesstelecom.store
  "SSoT for the wireless-telecom actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior `cloud-
  itonami-isic-*` actor in this fleet uses, including the sibling
  wired-telecom actor `cloud-itonami-isic-6190`'s own `telecom.store`:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/wirelesstelecom/store_contract_test.clj), which is the whole
  point: the actor, the Mobile Network Governor and the audit ledger
  never know which SSoT they run on.

  Like `telecom.store`'s dual number-provisioning/billing-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (provisioning an MSISDN, suspending
  service) acting on the SAME entity (a subscriber line, tied to a
  cell site's spectrum-license scope), each with its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:msisdn-provisioned?`/`:service-suspended?`, never a
  `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which line was
  screened for an unresolved spectrum-license dispute, which MSISDN
  was provisioned, which subscriber's service was suspended, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting a mobile
  network operator needs, and the evidence an operator needs if a
  provisioning or suspension decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [wirelesstelecom.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (line [s id])
  (all-lines [s])
  (license-screen-of [s line-id] "committed spectrum-license-dispute screening verdict for a line, or nil")
  (identity-verification-of [s line-id] "committed identity verification, or nil")
  (ledger [s])
  (provisioning-history [s] "the append-only MSISDN-provisioning history (wirelesstelecom.registry drafts)")
  (suspension-history [s] "the append-only service-suspension history (wirelesstelecom.registry drafts)")
  (next-provisioning-sequence [s jurisdiction] "next provisioning-number sequence for a jurisdiction")
  (next-suspension-sequence [s jurisdiction] "next suspension-number sequence for a jurisdiction")
  (line-already-provisioned? [s line-id] "has this line's MSISDN already been provisioned?")
  (line-already-suspended? [s line-id] "has this line's service already been suspended?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-lines [s lines] "replace/seed the line directory (map id->line)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained subscriber-line set covering both actuation
  lifecycles (provisioning an MSISDN, suspending service) so the actor
  + tests run offline."
  []
  {:lines
   {"line-1" {:id "line-1" :holder-name "Sakura Mobile Co-op"
              :msisdn "+819012345678"
              :site-id "site-jpn-01"
              :license-dispute-unresolved? false
              :msisdn-provisioned? false :service-suspended? false
              :jurisdiction "JPN" :status :intake}
    "line-2" {:id "line-2" :holder-name "Atlantis Wireless Co-op"
              :msisdn "+819012345679"
              :site-id "site-atl-01"
              :license-dispute-unresolved? false
              :msisdn-provisioned? false :service-suspended? false
              :jurisdiction "ATL" :status :intake}
    "line-3" {:id "line-3" :holder-name "鈴木回線"
              :msisdn "0312345678"
              :site-id "site-jpn-01"
              :license-dispute-unresolved? false
              :msisdn-provisioned? false :service-suspended? false
              :jurisdiction "JPN" :status :intake}
    "line-4" {:id "line-4" :holder-name "田中回線"
              :msisdn "+819012345680"
              :site-id "site-jpn-02"
              :license-dispute-unresolved? true
              :msisdn-provisioned? false :service-suspended? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- provision-msisdn!
  "Backend-agnostic `:line/mark-provisioned` -- looks up the line via
  the protocol and drafts the MSISDN-provisioning record, and returns
  {:result .. :line-patch ..} for the caller to persist."
  [s line-id]
  (let [ln (line s line-id)
        seq-n (next-provisioning-sequence s (:jurisdiction ln))
        result (registry/register-msisdn-provisioning line-id (:jurisdiction ln) seq-n)]
    {:result result
     :line-patch {:msisdn-provisioned? true
                 :provisioning-number (get result "provisioning_number")}}))

(defn- suspend-service!
  "Backend-agnostic `:line/mark-suspended` -- looks up the line via the
  protocol and drafts the service-suspension record, and returns
  {:result .. :line-patch ..} for the caller to persist."
  [s line-id]
  (let [ln (line s line-id)
        seq-n (next-suspension-sequence s (:jurisdiction ln))
        result (registry/register-service-suspension line-id (:jurisdiction ln) seq-n)]
    {:result result
     :line-patch {:service-suspended? true
                 :suspension-number (get result "suspension_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (line [_ id] (get-in @a [:lines id]))
  (all-lines [_] (sort-by :id (vals (:lines @a))))
  (license-screen-of [_ id] (get-in @a [:license-screens id]))
  (identity-verification-of [_ line-id] (get-in @a [:verifications line-id]))
  (ledger [_] (:ledger @a))
  (provisioning-history [_] (:provisionings @a))
  (suspension-history [_] (:suspensions @a))
  (next-provisioning-sequence [_ jurisdiction] (get-in @a [:provisioning-sequences jurisdiction] 0))
  (next-suspension-sequence [_ jurisdiction] (get-in @a [:suspension-sequences jurisdiction] 0))
  (line-already-provisioned? [_ line-id] (boolean (get-in @a [:lines line-id :msisdn-provisioned?])))
  (line-already-suspended? [_ line-id] (boolean (get-in @a [:lines line-id :service-suspended?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :line/upsert
      (swap! a update-in [:lines (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :license-screen/set
      (swap! a assoc-in [:license-screens (first path)] payload)

      :line/mark-provisioned
      (let [line-id (first path)
            {:keys [result line-patch]} (provision-msisdn! s line-id)
            jurisdiction (:jurisdiction (line s line-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:provisioning-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lines line-id] merge line-patch)
                       (update :provisionings registry/append result))))
        result)

      :line/mark-suspended
      (let [line-id (first path)
            {:keys [result line-patch]} (suspend-service! s line-id)
            jurisdiction (:jurisdiction (line s line-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:suspension-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lines line-id] merge line-patch)
                       (update :suspensions registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-lines [s lines] (when (seq lines) (swap! a assoc :lines lines)) s))

(defn seed-db
  "A MemStore seeded with the demo subscriber-line set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :license-screens {} :ledger [] :provisioning-sequences {}
                           :provisionings [] :suspension-sequences {} :suspensions []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/license-screen payloads, ledger
  facts, provisioning/suspension records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:line/id                          {:db/unique :db.unique/identity}
   :verification/line-id             {:db/unique :db.unique/identity}
   :license-screen/line-id           {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :provisioning/seq                 {:db/unique :db.unique/identity}
   :suspension/seq                   {:db/unique :db.unique/identity}
   :provisioning-sequence/jurisdiction {:db/unique :db.unique/identity}
   :suspension-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- line->tx [{:keys [id holder-name msisdn site-id
                         license-dispute-unresolved?
                         msisdn-provisioned? service-suspended?
                         jurisdiction status provisioning-number suspension-number]}]
  (cond-> {:line/id id}
    holder-name                            (assoc :line/holder-name holder-name)
    msisdn                                 (assoc :line/msisdn msisdn)
    site-id                                (assoc :line/site-id site-id)
    (some? license-dispute-unresolved?)   (assoc :line/license-dispute-unresolved? license-dispute-unresolved?)
    (some? msisdn-provisioned?)           (assoc :line/msisdn-provisioned? msisdn-provisioned?)
    (some? service-suspended?)            (assoc :line/service-suspended? service-suspended?)
    jurisdiction                          (assoc :line/jurisdiction jurisdiction)
    status                                (assoc :line/status status)
    provisioning-number                   (assoc :line/provisioning-number provisioning-number)
    suspension-number                     (assoc :line/suspension-number suspension-number)))

(def ^:private line-pull
  [:line/id :line/holder-name :line/msisdn :line/site-id
   :line/license-dispute-unresolved? :line/msisdn-provisioned? :line/service-suspended?
   :line/jurisdiction :line/status :line/provisioning-number :line/suspension-number])

(defn- pull->line [m]
  (when (:line/id m)
    {:id (:line/id m) :holder-name (:line/holder-name m)
     :msisdn (:line/msisdn m) :site-id (:line/site-id m)
     :license-dispute-unresolved? (boolean (:line/license-dispute-unresolved? m))
     :msisdn-provisioned? (boolean (:line/msisdn-provisioned? m))
     :service-suspended? (boolean (:line/service-suspended? m))
     :jurisdiction (:line/jurisdiction m) :status (:line/status m)
     :provisioning-number (:line/provisioning-number m) :suspension-number (:line/suspension-number m)}))

(defrecord DatomicStore [conn]
  Store
  (line [_ id]
    (pull->line (d/pull (d/db conn) line-pull [:line/id id])))
  (all-lines [_]
    (->> (d/q '[:find [?id ...] :where [?e :line/id ?id]] (d/db conn))
         (map #(pull->line (d/pull (d/db conn) line-pull [:line/id %])))
         (sort-by :id)))
  (license-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?k :license-screen/line-id ?lid] [?k :license-screen/payload ?p]]
              (d/db conn) id)))
  (identity-verification-of [_ line-id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?a :verification/line-id ?lid] [?a :verification/payload ?p]]
              (d/db conn) line-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (provisioning-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :provisioning/seq ?s] [?e :provisioning/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (suspension-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :suspension/seq ?s] [?e :suspension/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-provisioning-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :provisioning-sequence/jurisdiction ?j] [?e :provisioning-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-suspension-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :suspension-sequence/jurisdiction ?j] [?e :suspension-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (line-already-provisioned? [s line-id]
    (boolean (:msisdn-provisioned? (line s line-id))))
  (line-already-suspended? [s line-id]
    (boolean (:service-suspended? (line s line-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :line/upsert
      (d/transact! conn [(line->tx value)])

      :verification/set
      (d/transact! conn [{:verification/line-id (first path) :verification/payload (enc payload)}])

      :license-screen/set
      (d/transact! conn [{:license-screen/line-id (first path) :license-screen/payload (enc payload)}])

      :line/mark-provisioned
      (let [line-id (first path)
            {:keys [result line-patch]} (provision-msisdn! s line-id)
            jurisdiction (:jurisdiction (line s line-id))
            next-n (inc (next-provisioning-sequence s jurisdiction))]
        (d/transact! conn
                     [(line->tx (assoc line-patch :id line-id))
                      {:provisioning-sequence/jurisdiction jurisdiction :provisioning-sequence/next next-n}
                      {:provisioning/seq (count (provisioning-history s)) :provisioning/record (enc (get result "record"))}])
        result)

      :line/mark-suspended
      (let [line-id (first path)
            {:keys [result line-patch]} (suspend-service! s line-id)
            jurisdiction (:jurisdiction (line s line-id))
            next-n (inc (next-suspension-sequence s jurisdiction))]
        (d/transact! conn
                     [(line->tx (assoc line-patch :id line-id))
                      {:suspension-sequence/jurisdiction jurisdiction :suspension-sequence/next next-n}
                      {:suspension/seq (count (suspension-history s)) :suspension/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-lines [s lines]
    (when (seq lines) (d/transact! conn (mapv line->tx (vals lines)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:lines ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [lines]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-lines s lines))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo subscriber-line set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
