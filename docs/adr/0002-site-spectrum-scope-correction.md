# ADR-0002: Site/spectrum-license scope correction, jurisdiction-fabrication removal

## Status

Accepted.

## Context

ADR-0001 promoted `cloud-itonami-isic-6120` to `:implemented` with a
module shape (`wirelesstelecom.*`) that its own Decision 1 describes as
mirroring `cloud-itonami-isic-6190` (the wired VoIP/reseller telecom
actor) "op-for-op": a subscriber `line` entity, per-jurisdiction
identity-verification evidence checklists, MSISDN provisioning, and
service suspension.

Two problems surfaced on review before that implementation had a CI
workflow, a broken `deps.edn`, or any test that exercised the compiled
StateGraph end-to-end:

1. **Wrong sibling mirrored.** This repo's own README `Scope note`
   section exists specifically to distinguish `6120` from `6190`:
   `6190` "does not own spectrum or network infrastructure," while
   `6120` is "deliberately scoped to the SEPARATE business of holding a
   spectrum license and operating the physical radio access network
   (cell towers, base stations, RAN equipment)." `blueprint.edn` itself
   carries `:itonami.blueprint/robotics true` and the README's own
   `Robotics premise` paragraph describes "robots (tower/antenna
   deployment, base-station maintenance, site inspection)" operating
   under this actor's governor. The ADR-0001 implementation had NONE of
   this: no site/tower entity, no spectrum-license-status field, no
   site-access-record field, no build/deployment/maintenance op, no
   robotics-adjacent op at all. It implemented `6190`'s subscriber-
   identity/reseller shape instead of `6120`'s own published
   infrastructure-operator shape.

2. **Fabricated jurisdiction-specific regulatory content.**
   `wirelesstelecom.facts`'s `catalog` cited, as if verified: Japan's
   電波法 (Radio Act) and 携帯電話不正利用防止法 under 総務省 (MIC); the
   US Communications Act of 1934 Title III and 47 CFR Part 64 Subpart U
   under the FCC; the UK Wireless Telegraphy Act 2006 / Communications
   Act 2003 under Ofcom; and Germany's Telekommunikationsgesetz §172
   under the Bundesnetzagentur. None of these citations appear anywhere
   in this repo's own pre-existing `README.md` / `docs/business-
   model.md` / `docs/operator-guide.md` (all published before any
   implementation), and they were not independently verified before
   being written into `facts.cljc` as governed reference data. This
   violates this fleet's own "coverage is reported HONESTLY... never
   invent a jurisdiction's requirements" discipline that
   `wirelesstelecom.facts`'s own docstring claimed to follow.

Separately (not scope errors, but defects): `deps.edn` pointed at
`../../com-junkawasaki/langgraph-clj` / `langchain-clj`, paths that no
longer exist locally (those libraries were transferred+renamed to
`kotoba-lang/langgraph` / `langchain`, matching every sibling
`cloud-itonami-isic-*` actor's current `deps.edn` -- see CLAUDE.md
"Repo naming -- no `-clj` suffix"); `wirelesstelecom.store` hand-rolled
its own `enc`/`dec*` EDN-blob codec and Datomic schema/pull/tx plumbing
instead of using `kotoba-lang/langchain-store` (ADR-2607141600); there
was no `.github/workflows/ci.yml`; and no test in
`test/wirelesstelecom/` ever built and ran the compiled
`langgraph.graph` StateGraph end-to-end (`operation.cljc` itself was
never exercised by any test).

## Decision

### Decision 1: rebuild the domain layer around a SITE entity, following `tobaccoops` (cloud-itonami-isic-0115) as the reference template, not `telecom` (cloud-itonami-isic-6190)

The primary entity is now a registered `site` (a cell tower / base
station), the direct analog of a registered `field` in
`tobaccoops.store` (cloud-itonami-isic-0115) -- the sibling this repo's
own task brief names as the closest precedent for "well-specified
blueprint stub built to a full `:implemented` actor." A site carries a
structural `:spectrum-license-status` (must literally equal `:active`
before `:activate-tower` may commit) and a structural
`:site-access-record` (must be present before `:provision-subscriber`
may commit) -- both re-derived independently by the Governor from the
site's own on-file record, never trusted from the advisor's proposal,
the same "ground truth, not self-report" discipline every sibling
governor's guards establish. Site registration itself is an out-of-band
operator action (`store/add-site`, done during onboarding / license
renewal / site-access grant per `docs/operator-guide.md` "First
Deployment"), never a governed proposal -- mirroring how
`tobaccoops.store`'s field registration works.

### Decision 2: seven ops, all `:effect :propose`, covering this repo's own published Offer/Trust-Control bullets

`:log-network-build-record` (robotics-assisted tower/base-station
deployment or maintenance record -- equipment count and build-status
independently verified), `:schedule-site-operation` (deployment/
maintenance/inspection scheduling), `:activate-tower` (HARD:
spectrum-license-status), `:provision-subscriber` (HARD:
site-access-record), `:log-billing-record` (HARD: usage-evidence cited
-- `docs/business-model.md`'s "billing records require verified usage
evidence" Trust Control), `:flag-network-fault` (ALWAYS escalates --
covers equipment failure/interference/structural concerns and the
safety-critical out-of-band-spectrum/tower-climb/high-voltage site work
the README's own `Robotics premise` paragraph names), and
`:order-equipment` (cost-threshold escalation, categories drawn from
`wirelesstelecom.facts/equipment-categories`). Two permanently blocked
ops mirror `tobaccoops.governor/blocked-ops`:
`:operate-tower-equipment` (direct radio/tower-equipment actuation --
the robot's own direct-command channel, never this back-office
coordinator) and `:finalize-spectrum-license-decision` (a spectrum-
license grant/renewal/revocation decision is the regulator/licensee's
own act, never this coordinator's).

### Decision 3: `wirelesstelecom.facts` carries NO jurisdiction-specific regulatory content

Removed the fabricated per-country catalog entirely. The namespace now
holds only structural reference data this actor's own governor
independently needs: a closed `build-status-codes` vocabulary, an
`equipment-categories` cost-threshold table, and an informational
`site-operation-types` set -- none of it citing any specific
jurisdiction's spectrum regulator, statute, or fee schedule. Extending
this actor with real per-jurisdiction spectrum-licensing requirements
is legitimate future work, but it needs actual verification (a real
citation to an official source, the same `:provenance` discipline
`wirelesstelecom.facts`'s OLD docstring described but did not actually
follow) before it goes back into governed reference data -- it must
never be reconstructed from an LLM's unverified general knowledge.

### Decision 4: fix `deps.edn`, adopt `langchain-store`

`deps.edn` now points at `kotoba-lang/langgraph` / `langchain` /
`langchain-store` (matching every sibling actor's current `deps.edn`,
not the retired `com-junkawasaki/langgraph-clj` / `langchain-clj`
paths). `wirelesstelecom.store` now uses `langchain-store.core`'s
`identity-schema`/`enc`/`dec*`/`read-stream`/`append-blob!` instead of
hand-rolling its own EDN-blob codec and Datomic plumbing
(ADR-2607141600).

### Decision 5: add CI and a real end-to-end operation test

`.github/workflows/ci.yml` (lint + test, sibling-checkout pattern for
the `:local/root` deps, matching cloud-itonami-isic-0115/869/0111) and
`test/wirelesstelecom/operation_test.cljc` (builds the REAL compiled
`langgraph.graph` StateGraph via `operation/build` and runs it via
`langgraph.graph/run*` through commit / hard-hold (unregistered site /
inactive spectrum license / missing site-access record) / escalate-
approve / escalate-reject / phase-0-forces-escalation) are both new --
neither existed before this ADR.

## Alternatives considered

- **Patch `wirelesstelecom.facts`'s citations in place (soften the
  wording) while keeping the `6190`-mirrored subscriber-identity/MSISDN
  shape.** Rejected: the deeper problem was not the citation wording,
  it was that the whole module shape modeled the wrong business (a
  reseller identity-verification concern) instead of this repo's own
  published scope (spectrum-licensed infrastructure ownership +
  robotics-assisted physical deployment). Softening citations on a
  wrong-shaped model would not have closed the actual gap between
  `blueprint.edn`'s `:robotics true` and an implementation with zero
  site/tower/robotics modeling.
- **Keep the subscriber `line`/MSISDN-provisioning/service-suspension
  actuation pair as additional ops alongside the new site-centric
  ops.** Rejected for this pass: that level of subscriber-identity-
  verification/E.164-format-validation modeling is `6190`'s own
  domain concern (a reseller managing subscriber numbering), not a
  distinguishing feature of `6120`'s infrastructure-operator scope.
  `:provision-subscriber` (site-access-gated) already covers this
  repo's own README/business-model.md "subscriber provisioning" Offer
  bullet at the fidelity level this repo's own docs actually specify,
  without re-importing `6190`'s reseller-specific identity-
  verification machinery. A future pass MAY add deeper subscriber-
  identity modeling if a real jurisdiction-specific requirement is
  independently verified and cited (see Decision 3).

## Consequences

- `blueprint.edn`'s `:itonami.blueprint/robotics true` and
  `:required-technologies [:robotics ...]` are now actually reflected
  in the op set (`:log-network-build-record` /
  `:schedule-site-operation` name the robotics-assisted deployment/
  maintenance/inspection work the README's `Robotics premise` paragraph
  describes), where the ADR-0001 implementation had none.
  `:itonami.blueprint/implemented-slice` (see `blueprint.edn`)
  describes the corrected, current implementation.
- No governed reference data in this repo cites an unverified
  jurisdiction-specific spectrum-licensing statute, regulator name, or
  fee schedule. Extending real per-jurisdiction coverage remains
  legitimate future work, gated on actual verification per Decision 3.
- `deps.edn` resolves against this workspace's current sibling
  checkouts; CI actually exercises `clojure -M:dev:test` and
  `clojure -M:dev:run` against a fresh sibling-checkout, not a stale
  path that would fail on any clone made after the langgraph-clj →
  kotoba-lang/langgraph rename.
- The compiled StateGraph (`operation/build`) is now proven end-to-end
  by a real test suite (49 tests / 183 assertions,
  `test/wirelesstelecom/operation_test.cljc` included), not merely
  present in source with no test ever having invoked it.
