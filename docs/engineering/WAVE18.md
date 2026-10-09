# OULA Engineering Wave 18 — Human Review & Intervention Outcome Evidence

## Objective
Close the verified feedback loop from an immutable operational recommendation to a human-attested review and an optional follow-up observation, without confounding recommendation, authority, external execution, effect, or causation.

## Domain loop
Property Vital Snapshot → Operational Advisory → Human Review → Optional independently authorized Work Order → New Vital Assessment → Observed Before/After Status → Reality Memory candidate.

## Delivered
- `interventions` bounded context, depending only on public `advisory`, `vitals`, `operations`, IAM and platform seams;
- V019 append-only `interventions.review` and `interventions.outcome_observation` tables;
- compound PostgreSQL foreign keys tying source and after snapshots and Work Orders to the **same workspace and property**;
- one human decision per source snapshot / dimension / action; once recorded, it cannot be rewritten;
- human decisions `ACKNOWLEDGED`, `DECLINED`, `DEFERRED` — **none authorize execution**;
- recommendation verified against CURRENT advisory source and action code, including stale-data gate;
- after-state must be latest, later than both review and baseline, and produced by same Vital policy key/version;
- optional executed Work Order must be `COMPLETED`, evidence-backed, property-scoped, and after the review but before follow-up assessment;
- direction `IMPROVED`, `WORSENED`, `UNCHANGED`, `NOT_COMPARABLE`, with UNKNOWN never converted into a verified status;
- execution evidence level `OBSERVATION_ONLY` versus `VERIFIED_WORK_ORDER`, always distinguishing the absence of proof;
- explicit audit and Outbox events for review and observation;
- API with independent OAuth scopes for human-review creation, outcome entry and read history; idempotency and replay guards.

## New APIs
- `POST /v1/properties/{propertyId}/intervention-reviews`
- `POST /v1/intervention-reviews/{reviewId}/outcome`
- `GET /v1/properties/{propertyId}/intervention-reviews`

## Non-negotiable scientific / legal boundaries
- This wave **does not** execute actions, move funds, authorize maintenance, issue official documents, or infer a causal effect.
- `ACKNOWLEDGED` is advisory acknowledgement only and cannot substitute for the Compliance Approval Kernel.
- `VERIFIED_WORK_ORDER` refers only to an OULA evidence-backed completion record; it is not independent proof of service quality or source of funds.
- A changed Property Vital Sign is a descriptive observation subject to confounding, time drift, changing source data, policy changes, and selection bias.
- Future effectiveness studies require counterfactual design, pre-registered evaluation, sufficient controls and qualified review.

## Exit criteria
- PostgreSQL/PostGIS Flyway V001–V019 passes, including append-only triggers and scoped foreign keys.
- Human-only purpose and independent OAuth scopes enforced by API and service.
- Cross-workspace and non-proposed action rejection validated.
- Duplicate writes idempotent; inconsistent replay rejected.
- Post-action after snapshot must follow the actual review; policy/version mismatch is rejected.
- Existing Maven regressions and Spring Modulith verified; CodeQL green.
