# Business Model: Community Mobile Network Infrastructure Operations

## Classification
- Repository: `cloud-itonami-6120`
- ISIC Rev.5: `6120` — wireless telecommunications activities (this
  repository: licensed mobile network operator scope)
- Social impact: connectivity, digital inclusion, spectrum stewardship

## Customer
- independent/community mobile network operators needing an auditable
  spectrum-compliance platform
- resellers (including `cloud-itonami-isic-6190`'s own VoIP/reseller
  operators) needing wholesale wireless access
- regulators needing verifiable spectrum-license and site-access
  records
- programs that cannot accept closed, unauditable network-management
  platforms

## Offer
- spectrum-license and site-access scope management
- robotics-assisted tower/base-station deployment and maintenance
- subscriber provisioning and network-build records
- billing and usage records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per site/tower
- support retainer with SLA
- tower/base-station deployment robot integration and maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (operating outside a licensed spectrum band,
  tower-climb or high-voltage work) require human sign-off
- a deployment cannot be dispatched outside its verified spectrum-
  license scope
- billing records require verified usage evidence
- sensitive subscriber and site data stays outside Git
