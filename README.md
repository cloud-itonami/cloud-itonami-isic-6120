# cloud-itonami-6120

Open Business Blueprint for **ISIC Rev.5 6120**: wireless
telecommunications activities -- here scoped specifically to a
spectrum-licensed MOBILE NETWORK OPERATOR (owning and operating the
radio access network itself: cell towers, base stations, licensed
spectrum), distinct from a VoIP/reseller service.

This repository designs a forkable OSS business for community mobile
network infrastructure operations: spectrum-license and site-access
management, robotics-assisted tower/base-station deployment and
maintenance, and subscriber provisioning/billing records — run by a
qualified operator so a mobile network operator keeps its own
spectrum-compliance and network-maintenance history instead of renting
a closed telecom-management platform.

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

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (tower/antenna
deployment, base-station maintenance, site inspection) operate under
an actor that proposes actions and an independent **Mobile Network
Governor** that gates them. The governor never dispatches a network
operation itself; `:high`/`:safety-critical` actions (operating outside
a licensed spectrum band, any tower-climb or high-voltage work) require
human sign-off.

## Core Contract

```text
intake + identity + spectrum-license scope + subscriber provisioning
        |
        v
Network Operations Advisor -> Mobile Network Governor -> license record, dispatch, billing record, or human approval
        |
        v
robot actions (gated) + build/maintenance record + billing record + audit ledger
```

No automated advice can dispatch a network operation the governor
refuses, approve a deployment outside its verified spectrum-license
scope, or publish a billing record without governor approval and audit
evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `6120`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/phone`](https://github.com/kotoba-lang/phone) — E.164 numbering, SIP URIs, call/SMS records (shared with `cloud-itonami-isic-6190`, same as `:robotics` is shared fleet-wide)

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
