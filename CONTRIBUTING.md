# Contributing

`cloud-itonami-6120` accepts contributions to the OSS blueprint, capability
bindings, policy tests, documentation and operator model.

## Development
The capability layer lives in `kotoba-lang/robotics` and
`kotoba-lang/phone`. This repo holds the business blueprint and
operator contracts.

```bash
clojure -X:test
clojure -M:lint
```

## Rules
- Do not commit real subscriber, site or billing data.
- Keep robot dispatch, spectrum-license approvals and billing records
  behind the Mobile Network Governor.
- Treat deployment/maintenance workflows as high-risk: add tests for
  robot-safety gating, license scope, evidence, disclosure and audit
  logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
