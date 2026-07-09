# Operator Guide

## First Deployment
1. Register operator, sites/towers, spectrum licenses, staff and
   robots.
2. Import existing subscriber-provisioning and billing history.
3. Run read-only spectrum-license-scope and tower/base-station robot
   mission dry-runs.
4. Configure safety-class allowed sets and human sign-off paths.
5. Publish a dry-run billing record and audit export.

## Minimum Production Controls
- spectrum-license-scope validation before any deployment
- governor gate on every robot action before dispatch
- human sign-off for :high/:safety-critical actions (out-of-band
  operation, tower-climb/high-voltage work)
- evidence-backed billing records
- audit export for every dispatch, sign-off and billing record
- backup manual network-operations process

## Certification
Certified operators must prove robot-safety integrity, spectrum-
license discipline, evidence-backed billing records and human review
for deployment-affecting actions.
