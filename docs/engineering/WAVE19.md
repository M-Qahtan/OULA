# OULA Engineering Wave 19 — Rental Advisory Human Decision & Documentary Outcome Feedback

## Context

Wave 18 on `main` owns operational Vital Signs intervention reviews and
before/after source snapshots. Wave 19 complements it with a narrow, separately
governed record for **rental** advisory choices: renewal, rent documentary
coverage, recorded checkout and UNKNOWN occupancy status.

Both systems preserve the same distinctions: recommendation ≠ execution;
observation ≠ verified causal effect; rental document ≠ bank confirmation.

## Implemented loop

Current Rental Lifecycle Advisory
→ Version and action/source-matched Capture
→ Immutable Human Review Case
→ Append-only human dispositions (ACCEPT_FOR_REVIEW, DECLINE, DEFER)
→ Optional independently VERIFIED `ADVISORY_OUTCOME` documentary observation
→ Append-only feedback evidence timeline
→ Read-only documentary coverage counts.

## Data integrity

- Flyway V020 (V019 remains owned by operational interventions).
- `advisory.review_case`: immutable source metrics and generated-at fingerprint.
- `advisory.review_event`: sequenced human disposition/outcome references.
- Case row lock serializes concurrent decisions and observations.
- After an observed outcome the original decision cannot be overwritten.
- Outcome requires most recent human disposition to accept review.
- VERIFIED evidence of type ADVISORY_OUTCOME and same workspace is required.
- All mutations audited, outboxed, correlation-traced and HTTP-idempotent.
- Human disposition is not a work approval or payment authorization.
- No code attempts automatic Reality Memory training or legal collection action.

## Endpoints

- POST /v1/properties/{propertyId}/advisory-reviews
- POST /v1/advisory-reviews/{caseId}/decisions
- POST /v1/advisory-reviews/{caseId}/outcomes
- GET /v1/advisory-reviews/{caseId}
- GET /v1/properties/{propertyId}/advisory-feedback-summary

## Evidence quality

Count of cases with an observed improvement is **not** a success rate.
Reasons include selection bias, absent counterfactuals, source truth gaps and
unknown physical occupancy. Future effectiveness studies must separately
validate outcomes against measured ground truth.

## Exit gates

- [ ] Flyway V019 (interventions) → V020 (rental review)
- [ ] Both operational and rental feedback regression suites
- [ ] Append-only history enforcement and workspace-scoped evidence
- [ ] Idempotency, purpose, scope and read-only feedback summary
- [ ] Spring Modulith and CodeQL green
