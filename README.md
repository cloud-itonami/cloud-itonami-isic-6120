# cloud-itonami-isic-6120

Open Business Blueprint for **ISIC Rev.5 6120**: wireless
telecommunications activities -- here scoped specifically to a
spectrum-licensed MOBILE NETWORK OPERATOR (owning and operating the
radio access network itself: cell towers, base stations, licensed
spectrum), distinct from a VoIP/reseller service.

This repository publishes a mobile-network-operator actor -- subscriber
line intake, identity verification, spectrum-license-dispute
screening, MSISDN provisioning and service suspension -- as an OSS
business that any qualified, spectrum-licensed community mobile
network operator can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet, most closely
[`cloud-itonami-isic-6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190)
(the sibling wired/VoIP-reseller telecom actor -- SAME ISIC 61xx
telecommunications industry, distinct wireless-vs-wired/spectrum-
licensed-vs-reseller scope). Here it is **Network Operations Advisor ⊣
Mobile Network Governor** (`:mobile-network-governor` in this repo's
own `blueprint.edn`).

> **Why an actor layer at all?** An LLM is great at drafting a line-
> intake summary, normalizing records, and checking whether a line's
> own recorded MSISDN is even syntactically well-formed -- but it has
> **no notion of which jurisdiction's spectrum-licensing/subscriber-
> registration requirements are official, no license to provision a
> real MSISDN or suspend a real subscriber's service, and no way to
> know on its own whether a spectrum-license dispute against the
> line's site has actually stayed unresolved**. Letting it provision an
> MSISDN or suspend service directly invites fabricated spectrum-
> licensing citations, a subscriber provisioned on a malformed MSISDN,
> and an unresolved license dispute being quietly ignored -- and
> liability, and consumer-protection risk, for whoever runs it. This
> project seals the Network Operations Advisor into a single node and
> wraps it with an independent **Mobile Network Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope note: licensed network operator, not a reseller

`cloud-itonami-isic-6190` ("Community Telecommunications Access") is
explicitly a VoIP/reseller/public-access business -- its own
`docs/business-model.md` names its activity as "VoIP, public access,
reselling," and it does not own spectrum or network infrastructure.
This repository is deliberately scoped to the SEPARATE business of
holding a spectrum license and operating the physical radio access
network (cell towers, base stations, RAN equipment) that a reseller
like `6190` would need to buy wholesale access to -- a genuinely
distinct, more heavily regulated business (spectrum-license compliance
and site-access rights are core regulatory concerns that a pure
reseller never faces). `cloud-itonami-isic-6110` ("Wired
Telecommunications Network Operations") covers the wired/fiber
infrastructure side; this repository covers the wireless side.

### What this actor does and does not do

This actor covers subscriber-line intake through identity
verification, spectrum-license-dispute screening, MSISDN provisioning
and service suspension. It does **not**, by itself, hold any spectrum
license or site-access right required to operate a mobile network in a
given jurisdiction, and it does not claim to. It also does **not**
model a real HLR/HSS/radio-access-network element, real-time network
telemetry, or lawful-intercept infrastructure -- no live spectrum-
occupancy monitoring, no real base-station dispatch (see
`wirelesstelecom.facts`'s own docstring for the honest simplification
this makes: a starting catalog of spectrum-licensing authorities, not
a survey of every jurisdiction's variant). Whoever deploys and
operates a live instance (a licensed mobile network operator) supplies
the real spectrum license, the real radio access network and any real
lawful-intercept/emergency-path integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch for every new market.

### Actuation

**Provisioning a real MSISDN or suspending a real subscriber's service
is never autonomous, at any phase, by construction.** Two independent
layers enforce this (`wirelesstelecom.governor`'s `:actuation/
provision-msisdn`/`:actuation/suspend-service` high-stakes gate and
`wirelesstelecom.phase`'s phase table, which never puts either op in
any phase's `:auto` set) -- see `wirelesstelecom.phase`'s docstring
and `test/wirelesstelecom/phase_test.clj`'s `provision-msisdn-never-
auto-at-any-phase`/`suspend-service-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human network operator is
always the one who actually provisions an MSISDN or suspends service.
Like `cloud-itonami-isic-6190`'s own dual actuation, this actor has
TWO actuation events -- and like `6190`'s `:actuation/suppress-
billing-record`, **`:actuation/suspend-service` is a NEGATIVE
actuation**: it withholds ongoing connectivity rather than issuing a
new record -- the THIRD time this fleet has modeled a high-stakes act
in that direction (after `cloud-itonami-isic-3600`'s alert suppression
and `6190`'s billing-record suppression). See this actor's own
`docs/adr/0001-architecture.md` Decision 2 for the honest framing this
makes.

**Lawful-intercept, subscriber-location/call-detail disclosure, and
law-enforcement-ordered suspension are OUT OF SCOPE for this actor by
construction** -- there is no op, HARD check, or advisor dispatch
branch for any of them, mirroring `6190`'s own explicit "lawful-
intercept and emergency paths remain outside LLM control" posture. See
`docs/adr/0001-architecture.md` Decision 9.

## The core contract

```
line intake + jurisdiction facts (wirelesstelecom.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Network      │ ─────────────▶ │ Mobile                        │  (independent system)
   │ Operations   │  + citations    │ Network Governor:             │
   │ Advisor      │                 │ spec-basis · evidence-       │
   │ (sealed)     │                 │ incomplete · MSISDN-format-  │
   └──────────────┘         commit ◀────┼──────────▶ hold │ invalid (structural) ·
                                 │             │           │ license-dispute-
                           record + ledger  escalate ─▶ human   unresolved (unconditional) ·
                                             (ALWAYS for         already-provisioned/-suspended
                                              :actuation/provision-
                                              msisdn /
                                              :actuation/suspend-
                                              service)
```

**The Network Operations Advisor never provisions an MSISDN or
suspends service the Mobile Network Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
spectrum-licensing/subscriber-registration requirements; unsupported
evidence; a malformed MSISDN; an unresolved spectrum-license dispute;
a double provisioning or suspension) force **hold** and *cannot* be
approved past; a clean provisioning/suspension proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (tower/antenna
deployment, base-station maintenance, site inspection) operate under
the actor, gated by the independent **Mobile Network Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions (operating outside a licensed spectrum band, any tower-climb
or high-voltage work) require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Mobile Network Governor, MSISDN-provisioning + service-suspension draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6120`). Required capabilities are implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) -- missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/phone`](https://github.com/kotoba-lang/phone) -- E.164 numbering, SIP URIs, call/SMS records (shared with `cloud-itonami-isic-6190`, same as `:robotics` is shared fleet-wide)

`wirelesstelecom.*` cites these capability contracts for the shape of
a real MSISDN record/robot mission without requiring them directly,
the SAME "related capability contract but not required" posture
`telecom.*`/`credit`/`leasing`/`card` established -- the actor is fully
self-contained and runs offline with `MemStore`; a production
deployment wires the real capabilities in as its subscriber-management
and robot-dispatch backends.

## Layout

| File | Role |
|---|---|
| `src/wirelesstelecom/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate MSISDN-provisioning/service-suspension history. Both actuation ops act directly on a pre-seeded line, and the double-actuation guards check dedicated `:msisdn-provisioned?`/`:service-suspended?` booleans rather than a `:status` value |
| `src/wirelesstelecom/registry.cljc` | MSISDN-provisioning + service-suspension draft records, plus `msisdn-invalid-format?` (mirrors `telecom.registry/e164-invalid-format?`, `cloud-itonami-isic-6190`, applied to a mobile MSISDN) |
| `src/wirelesstelecom/facts.cljc` | Per-jurisdiction spectrum-licensing + subscriber-registration catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/wirelesstelecom/opsadvisor.cljc` | **Network Operations Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/license-dispute-screening/MSISDN-provisioning/service-suspension proposals |
| `src/wirelesstelecom/governor.cljc` | **Mobile Network Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · MSISDN-format-invalid, pure ground-truth structural recompute · license-dispute-unresolved, unconditional evaluation) + already-provisioned/already-suspended guards + 1 soft (confidence/actuation gate) |
| `src/wirelesstelecom/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both MSISDN provisioning and service suspension always human; line intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/wirelesstelecom/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/wirelesstelecom/sim.cljc` | demo driver |
| `test/wirelesstelecom/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers subscriber-line intake through identity
verification, spectrum-license-dispute screening, MSISDN provisioning
and service suspension -- the core governed lifecycle this blueprint's
own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Line intake + per-jurisdiction spectrum-licensing/subscriber-registration checklisting, HARD-gated on an official spec-basis citation (`:line/intake`/`:identity/verify`) | Real HLR/HSS/radio-access-network integration, real-time spectrum-occupancy telemetry (see `wirelesstelecom.facts`'s docstring) |
| Spectrum-license-dispute screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:license/screen`) | Real robot dispatch for tower/base-station deployment or maintenance |
| MSISDN provisioning, HARD-gated on full evidence and MSISDN structural validity, plus a double-provisioning guard (`:actuation/provision-msisdn`) | Lawful-intercept, subscriber-location/call-detail disclosure, and law-enforcement-ordered suspension (deliberately outside LLM/actor control -- see `docs/adr/0001-architecture.md` Decision 9) |
| Service suspension, HARD-gated on full evidence and a double-suspension guard (`:actuation/suspend-service`) | |
| Immutable audit ledger for every intake/verification/screening/provisioning/suspension decision | |

Extending coverage is additive: add the next gate (e.g. a roaming-
request check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`wirelesstelecom.facts/coverage` reports how many requested
jurisdictions actually have an official spec-basis in
`wirelesstelecom.facts/catalog` -- currently 4 seeded (JPN, USA, GBR,
DEU) out of ~194 jurisdictions worldwide. This is a starting catalog
to prove the governor contract end-to-end, not a claim of global
coverage. Adding a jurisdiction is additive: one map entry in
`wirelesstelecom.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `Network Operations Advisor` + `Mobile Network
Governor` run as real, tested code (see `Run` above), promoted from
the originally-published `:blueprint`-tier scaffold, modeled closely
on `cloud-itonami-isic-6190`'s architecture (the same ISIC 61xx
telecommunications industry). See `docs/adr/0001-architecture.md` for
the history and design.

## License

AGPL-3.0-or-later.
