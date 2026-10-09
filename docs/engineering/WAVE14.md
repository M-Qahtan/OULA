# OULA Engineering Wave 14 — Evidence-Aware Property Vital Trends

## Mission
Provide a controlled read-only comparison of immutable Property Vital Sign observations, without fabricating a forecast, causal explanation or prediction.

## End-to-end flow
Managed Property → Workspace/Purpose/Scope guard → Immutable Vital Snapshot History → Policy Compatibility Check → Eight Transparent Dimension Deltas → Descriptive Trend Response.

## Delivered
- bounded, workspace- and property-scoped history query ordered by assessment time and immutable ID;
- `GET /v1/properties/{propertyId}/vitals/trend` using existing `oula.property.vitals.read` authority;
- explicit `NO_HISTORY` and `INSUFFICIENT_HISTORY` results;
- exact policy-key and policy-version comparison before directional classification;
- `POLICY_NOT_COMPARABLE` on assessment-policy mismatch;
- eight named dimension comparisons with before/after states;
- UNKNOWN on either side is `NOT_COMPARABLE`, even UNKNOWN → UNKNOWN;
- overall `NOT_COMPARABLE` when no dimension supplies comparable evidence;
- direction `IMPROVED`, `WORSENED`, `MIXED`, or `UNCHANGED` for comparable observations;
- no mutation, new background work, external call, or autonomous approval from trend reads;
- OpenAPI 1.3.0 contract.

## Quality gates
- unit tests: no history, one snapshot, deterioration, mixed movements, policy mismatch, UNKNOWN handling, unauthorized purpose;
- real PostgreSQL/PostGIS API integration: first-vs-second assessment, scope denial;
- Flyway history and full regression suite;
- Spring Modulith verification and CodeQL;
- merge only after final-head checks succeed.

## Scientific limitation
Trend classification compares stored observations; it does not prove causality. Changes can reflect underlying data collection, timing and missingness. Future longitudinal research must explicitly account for data quality, policy versions, period selection and selection bias.

## Architectural boundary
This read model consumes canonical operational history. It never owns Property Truth, alters Work Orders, records payments or authorizes external agents.
