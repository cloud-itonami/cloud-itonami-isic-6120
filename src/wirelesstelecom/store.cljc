(ns wirelesstelecom.store
  "SSoT for the mobile-network-operator infrastructure coordinator,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every cloud-itonami actor in this fleet uses (mirrors
  `tobaccoops.store`, cloud-itonami-isic-0115; `cerealops.store`,
  cloud-itonami-isic-0111):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/wirelesstelecom/store_contract_test.cljc).

  A registered site is the minimal unit of authority: a cell tower /
  base-station site must be registered before ANY proposal referencing
  it can be considered by the Governor (see `wirelesstelecom.governor`'s
  `site-registered` invariant). Site data is opaque to this
  namespace -- callers/backends decide what a site record contains
  (name, location, `:spectrum-license-status`, `:site-access-record`,
  etc); this Store only answers 'is this site-id registered, and if so
  what's on file'. Because the site payload shape is intentionally
  open, `DatomicStore` stores it as a single opaque EDN-blob attribute
  (`:site/payload`, via `langchain-store.core`'s `enc`/`dec*`) rather
  than expanding it into per-key Datomic attributes -- the same blob
  convention every sibling DatomicStore already uses for its own opaque
  payloads.

  FIX (2026-07-23): a prior implementation attempt on this repo
  hand-rolled its own `enc`/`dec*` (`(defn- enc [v] (pr-str v))`) and
  its own Datomic schema/pull/tx plumbing instead of using
  `kotoba-lang/langchain-store` (ADR-2607141600: the seam ~190 actors
  had hand-rolled; `langchain-store.core` centralizes it). This
  namespace uses `ls/identity-schema` / `ls/enc` / `ls/dec*` /
  `ls/read-stream` / `ls/append-blob!` instead.

  The registered site is the ONLY entity this Store owns state for --
  site registration/updates (spectrum-license renewal, site-access
  grant) are an out-of-band operator action (see
  `docs/operator-guide.md` 'First Deployment': operator registers
  sites/towers/spectrum licenses before any proposal can reference
  them), the same discipline `tobaccoops.store`'s field registration
  uses. No governed proposal in `wirelesstelecom.operation` mutates a
  site record -- every op is a `:effect :propose` journal entry into
  the append-only ledger, verified against the site's CURRENT recorded
  state, never a state transition on the site record itself.

  The append-only audit ledger (`ledger`/`append-ledger!`) is this
  actor's SSoT for every committed/held/approval-rejected decision
  fact: `wirelesstelecom.operation`'s `:commit`/`:hold` graph nodes
  append every decision here, so a site's full operating history
  (every `:log-network-build-record` / `:schedule-site-operation` /
  `:activate-tower` / `:provision-subscriber` / `:log-billing-record` /
  `:flag-network-fault` / `:order-equipment` decision) is always a
  query over an immutable log. The ledger stays append-only on every
  backend. Sensitive subscriber and site data (see
  `docs/business-model.md` Trust Controls: 'sensitive subscriber and
  site data stays outside Git') lives only in this runtime Store, never
  in repo-committed fixtures."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (registered-site [store site-id]
    "Retrieve a registered site record by ID. Returns nil if the
    site-id is nil or not registered.")
  (add-site [store site-id site-data]
    "Register or update a site in the store. Used by tests, simulation,
    and operator onboarding (spectrum-license renewal, site-access
    grant).")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns fact."))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [sites ledger-atom]
  Store
  (registered-site [_store site-id]
    (when site-id
      (get @sites site-id)))
  (add-site [_store site-id site-data]
    (swap! sites assoc site-id site-data)
    site-data)
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-sites` is an optional map of
  site-id -> site-record."
  [& [{:keys [initial-sites] :or {initial-sites {}}}]]
  (MemStore. (atom initial-sites) (atom [])))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:site/payload` is stored as an EDN string blob (via
  `langchain-store.core`) so `langchain.db` doesn't try to expand an
  opaque, caller-defined site record into sub-entities. The identity-
  schema builder, EDN-blob codec and seq-keyed event-log read/append are
  the shared kotoba-lang/langchain-store machinery (ADR-2607141600) --
  the seam ~190 actors hand-roll; this store keeps only its domain
  wiring."
  (ls/identity-schema [:site/id :ledger/seq]))

(defrecord DatomicStore [conn]
  Store
  (registered-site [_store site-id]
    (when site-id
      (ls/dec* (d/q '[:find ?p .
                      :in $ ?sid
                      :where [?e :site/id ?sid] [?e :site/payload ?p]]
                    (d/db conn) site-id))))
  (add-site [_store site-id site-data]
    (d/transact! conn [{:site/id site-id :site/payload (ls/enc site-data)}])
    site-data)
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `initial-sites`
  (site-id -> site-record); empty when omitted."
  [& [{:keys [initial-sites] :or {initial-sites {}}}]]
  (let [s (->DatomicStore (d/create-conn schema))]
    (doseq [[site-id site-data] initial-sites]
      (add-site s site-id site-data))
    s))
