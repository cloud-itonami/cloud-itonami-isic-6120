# ADR-0001: Network Operations Advisor ⊣ Mobile Network Governor architecture

## Status

Accepted. `cloud-itonami-isic-6120` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-6120` publishes an OSS business blueprint for
community mobile network infrastructure operations: spectrum-license
and site-access scope management, subscriber-line intake, MSISDN/SIM
provisioning and service-suspension records, run by a qualified
spectrum-licensed mobile network operator. Like every prior actor in
this fleet, the blueprint alone is not an implementation: this ADR
records the governed-actor architecture that promotes it to real,
tested code, following the same langgraph-clj StateGraph + independent
Governor + Phase 0→3 rollout pattern established by
`cloud-itonami-isic-6511` (life insurance) and applied most closely by
`cloud-itonami-isic-6190` (wired/VoIP-reseller telecom, the SAME
industry -- ISIC 61xx telecommunications -- as this repo's own
wireless/mobile-network-operator scope).

## Decision

### Decision 1: mirror `cloud-itonami-isic-6190`'s module shape, adapt the domain to a licensed mobile network operator

This repo's own already-published `blueprint.edn`/`docs/business-
model.md`/`docs/operator-guide.md`/README (written before this
promotion) scope `cloud-itonami-isic-6120` explicitly as a
spectrum-licensed MOBILE NETWORK OPERATOR (owning/operating the radio
access network -- towers, base stations, licensed spectrum), distinct
from `6190`'s own explicit VoIP/reseller scope (no infrastructure or
spectrum) and from `6110`'s own wired/fiber network-operator scope.
This promotion keeps that published design's business framing intact
and mirrors `telecom.*` (`6190`)'s MODULE SHAPE exactly
(`facts`/`governor`/`operation`/`phase`/`registry`/`sim`/`store`/
`opsadvisor`, one file each, the same langgraph-clj StateGraph
skeleton) -- the same "mirror the sibling closest in industry, adapt
the domain" move `6110`'s own registry entry documents it made against
`6190` "op-for-op". The primary entity is a subscriber `line` (an
MSISDN/SIM record, tied to a cell site's spectrum-license scope via
`:site-id`), analogous to `telecom.store`'s `line` (a phone-number/
line record).

### Decision 2: `:actuation/suspend-service` is this fleet's THIRD negative actuation

Every actuation in this fleet prior to `cloud-itonami-isic-3600` was a
POSITIVE act: issuing or finalizing a real-world record. `3600`'s
`:actuation/suppress-alert` and `6190`'s `:actuation/suppress-billing-
record` both broke that pattern -- each WITHHOLDS/SILENCES something
rather than issuing it. This actor's `:actuation/suspend-service` is
the THIRD negative actuation in this fleet's history: it withholds
ongoing subscriber connectivity (e.g. for non-payment) rather than
issuing a new record. The governed-actor discipline (HARD checks,
high-stakes gate, phase-3 exclusion, dedicated double-actuation
boolean) generalizes cleanly to this third negative instance with no
special-casing required.

### Decision 3: entity and op shape

The primary entity is a `line` (a wireless subscriber's MSISDN/SIM
record, tied to a cell site's spectrum-license scope). Five ops:
`:line/intake` (directory upsert, no capital risk), `:identity/verify`
(per-jurisdiction subscriber-identity + spectrum-licensing evidence
checklist, never auto -- analogous to `telecom.operation`'s
`:identity/verify`), `:license/screen` (spectrum-license-dispute
screening on the line's site, unconditional-evaluation discipline,
never auto -- the wireless analog of `telecom.operation`'s `:billing/
screen`), `:actuation/provision-msisdn` (POSITIVE, high-stakes), and
`:actuation/suspend-service` (NEGATIVE, high-stakes). This is the SAME
dual-actuation-on-one-entity shape `telecom` (`6190`) and its own
prior siblings (`school`/`association`/`leasing`/`behavioral`/
`secondary`/`card`/`water`) all use.

### Decision 4: `msisdn-invalid-format?` reuses `6190`'s format/syntactic-validity check family, applied to a mobile MSISDN

`wirelesstelecom.registry/msisdn-invalid-format?` independently
recomputes whether a line's own recorded MSISDN is a syntactically
valid E.164 number (leading `+`, no leading zero, 8-15 total digits).
A mobile subscriber's MSISDN IS itself E.164-formatted, so this
DELIBERATELY reuses the SAME check shape `telecom.registry/e164-
invalid-format?` (`6190`) established as this fleet's first
format/syntactic-validity check family -- not a new "first instance"
claim (that precedent belongs to `6190`), but an honest second
application of the same family to a genuinely different real-world
identifier (a mobile MSISDN rather than a fixed-line/VoIP number). It
gates only `:actuation/provision-msisdn` (the point where a malformed
MSISDN would otherwise get provisioned for real use), the same
restricted-scope placement `6190`'s own `e164-format-invalid` check
uses.

### Decision 5: `license-dispute-unresolved-violations` -- the spectrum-license-dispute analog of `6190`'s billing-dispute check

Following the discipline `casualty.governor/sanctions-violations`
established and every prior sibling's unconditional-evaluation
screening family applies (most directly `telecom.governor/billing-
dispute-unresolved-violations`, `6190`), `license-dispute-unresolved-
violations` is evaluated UNCONDITIONALLY -- not scoped to a specific
op -- so `:license/screen` itself can HARD-hold on its own finding,
not merely gate the downstream actuation. Exercised in tests/demo via
`:license/screen` DIRECTLY against an already-flagged line, not via an
actuation op against an unscreened line -- the "screen the screening
op directly, not the actuation op" lesson `parksafety`'s
ADR-2607071922 Decision 5 established, and `6190`'s own ADR-0001
Decision 4 most recently reaffirmed.

### Decision 6: dedicated double-actuation-guard booleans

`:msisdn-provisioned?`/`:service-suspended?` are dedicated booleans on
the `line` record, never a single `:status` value -- the same
discipline every prior sibling governor's guards establish, informed
by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`wirelesstelecom.store/Store` is implemented by both `MemStore`
(atom-backed, default for dev/tests/demo) and `DatomicStore`
(`langchain.db`-backed), proven to satisfy the same contract in
`test/wirelesstelecom/store_contract_test.clj` -- the same seam every
sibling actor uses so swapping the SSoT backend is a configuration
change, not a rewrite.

### Decision 8: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:line/intake` (no
capital risk). `:identity/verify` and `:license/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/provision-msisdn`/`:actuation/suspend-
service` are permanently excluded from every phase's `:auto` set -- a
structural fact, not a rollout milestone, enforced by BOTH
`wirelesstelecom.phase` and `wirelesstelecom.governor`'s `high-stakes`
set independently.

### Decision 9: lawful-intercept / subscriber-location disclosure / law-enforcement-ordered suspension are OUT OF SCOPE by construction, mirroring `6190`'s own posture

This repo's own already-published `docs/business-model.md` Trust
Controls do not name lawful-intercept as in scope, and `6190`'s own
`docs/business-model.md` explicitly states "lawful-intercept and
emergency paths remain outside LLM control." This actor follows the
SAME exclusion, not a new design: there is no op, HARD check, or
`wirelesstelecom.opsadvisor/infer` dispatch branch for disclosing
subscriber location/call-detail records or for a law-enforcement-
ordered suspension distinct from the ordinary `:actuation/suspend-
service` (non-payment) op. A production deployment wires these
regulated paths through its own dedicated lawful-intercept
infrastructure and legal process, entirely outside this actor's LLM
advisor and governor -- the actor never proposes, and the governor
never has occasion to gate, an act it has no operation for. This is
the SAME "exclude, don't half-model" choice `6190` made for its own
closest analog, not a weaker gate.

### Decision 10: mock + LLM advisor pair

`wirelesstelecom.opsadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
provisioning an MSISDN or auto-suspending service).

### Decision 11: `blueprint.edn` field-sync fix

`:itonami.blueprint/id` was the stale pre-`isic-`-rename value
`"cloud-itonami-6120"`, while the repo folder, README title and this
actor's own `:business-id` in the `kotoba-lang/industry` registry
already use the corrected `cloud-itonami-isic-6120`. Fixed to match,
the same class of fix `6190`'s own ADR-0001 Decision 10 (and `card.
6619`'s, `water.3600`'s) documents.

## Alternatives considered

- **Modeling law-enforcement-ordered suspension as a distinct
  actuation op from ordinary non-payment suspension.** Rejected: this
  repo's own published Trust Controls, and `6190`'s explicit "lawful-
  intercept and emergency paths remain outside LLM control" precedent,
  place law-enforcement-directed action outside the actor entirely --
  adding a second suspension op for it would falsely imply the LLM
  advisor is a legitimate party to that decision.
- **Modeling subscriber-location/CDR disclosure as a governed
  actuation (HARD-gated + high-stakes, like provisioning/suspension).**
  Rejected for the same reason as above: `6190`'s own sibling scope
  note treats this class of act as categorically outside LLM/actor
  control, not merely a higher-stakes version of an in-scope op.
- **A single actuation (provisioning only), treating service
  suspension as a lower-stakes administrative note.** Rejected: this
  repo's own `docs/business-model.md` Trust Controls state "a
  deployment cannot be dispatched outside its verified spectrum-
  license scope" and "billing records require verified usage
  evidence," the same posture `6190`'s own ADR-0001 Decision 1 used to
  justify treating billing-record suppression as high-stakes on equal
  footing with number provisioning -- collapsing service suspension
  into a non-high-stakes op would contradict that same posture applied
  to subscriber service continuity.

## Consequences

- Confirms the negative-actuation pattern generalizes a third time
  (water-safety alerting, wired-telecom billing, wireless-telecom
  service continuity), not a one-off quirk of any single domain.
- Confirms `6190`'s format/syntactic-validity check family
  (`e164-invalid-format?`) generalizes to a second real-world
  identifier (a mobile MSISDN) without modification to its check
  shape.
- One pre-existing `blueprint.edn` inconsistency (stale
  pre-`isic-`-rename id) fixed as in-scope minor consistency work,
  consistent with how `6190`/`card.6619`/`water.3600` handled the same
  class of issue.
- `kotoba-lang/industry`'s `:blueprint` tier count decreases by one and
  `:implemented` increases by one; ISIC Wave 0 (ADR-2607121000)
  advances by one class.
