# OULA — Frontend / Backend source-of-truth integration contract
## Authority
Backend contract: `contracts/openapi.yaml` (currently v1.10.0). Backend Java is canonical; do NOT reverse engineer truth from an external Lovable demo. Frontend `apps/web` adapts to backend API; no direct DB from browser.
## UI-to-domain screen matrix
| User screen | Canonical owner | API expectations | Cross-cutting |
|---|---|---|---|
| Sign-in / Workspace | iam + people | OIDC JWT, member/workspace selection; exact API TBD | role + purpose + scope, explicit consent |
| LifeGraph Lite / Create Intent | people + intent | backend create/read structured intent may be MISSING; issue #39/#48 | individual vs user, field privacy |
| Search candidates / LifeFit | matching | existing POST `/v1/intents/{intentId}/matches` inspect request/response | hard constraints, version, explanation, idempotency |
| Property Passport | property | GET `/v1/properties/{propertyId}/passport` | typed response missing in current OpenAPI? verify and add; facts provenance |
| Compare / Decision | intelligence/decision | recommendation/explanation/record decision endpoints | human decision ≠ AI suggestion |
| Viewing / Offer / Deal Room | transaction | GET `/v1/transactions/{transactionId}`, POST transitions; viewing/offer seams may be missing | exact domain state labels, auth, audit and concurrency |
| Property Guardian | operations/advisory/vitals | property management, vitals and operational advice read APIs | no automatic external action |
| Reality Memory | intelligence | outcomes/timeline/reality-gap read APIs | no causal attribution |
| City/Built World lab | research | none deployed/verified | feature labeled FUTURE/SIMULATED |
## Header and control rules
HTTP bearer JWT issued by an authorized IdP (never static in browser code); workspace identity from verified JWT claim not spoofable headers; `X-OULA-Purpose` is requested purpose, server independently authorizes it. For mutation require `Idempotency-Key` and exact correlation, subject to existing API schema. Sensitive actions require human verified policy and actor scope.
## Error contract
No silent DEMO fallback from LIVE API errors; show authentication required, permission denied, unavailable, stale version, validation failed, retry only if idempotent. Use typed responses generated/verified against OpenAPI. If API schema missing, report issue and keep the screen DEMO with visible label until added and tests pass.
## Provenance contract
VERIFIED only with actual independent verification; DECLARED is first-party declaration; OBSERVED, CALCULATED, ESTIMATED, AI_INFERRED, DISPUTED, EXPIRED distinct. Fixture marked DEMO/SANDBOX. No false bank confirmations, property registry, government electronic contracts, escrow or certified valuations. Never treat rental installment due as paid.
## Delivery proof
A feature is REAL only with: running backend, auth, persisted DB, integrated browser, positive/negative tests and traceable response. A visually interactive mock remains DEMO even when delightful.
