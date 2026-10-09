# OULA Engineering Wave 13 — Evidence-Aware Vital Trends

## Scope
Read-only deterministic comparison of two immutable Property Vital Sign snapshots, scoped to the active workspace and managed property.

## Delivered
- bounded history query (1–100), ordered by assessment timestamp and immutable identifier;
- no-history and insufficient-history states;
- eight dimension comparisons with explicit before/after status;
- UNKNOWN transitions classified NOT_COMPARABLE, never assumed improvement;
- overall direction IMPROVED / WORSENED / MIXED / UNCHANGED;
- GET /v1/properties/{propertyId}/vitals/trend with existing PROPERTY_MANAGEMENT authorization and vitals.read scope.

## Trust invariants
- Trend != forecast, causal explanation, recommendation, or property fact.
- Policy version differences must be disclosed before comparing metrics.
- No external side effects, fund movements, work orders, or automatic escalations.
- No AI-generated confidence or fabricated signal.
- CI and CodeQL must pass before merging.
