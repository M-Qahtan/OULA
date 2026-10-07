# OULA Engineering Wave 05 — Reality Memory & Scientific Calibration

## Production slice

```
Property State
  -> Recommendation
  -> Human Decision
  -> Outcome
  -> Reality Gap
  -> Reviewed Error Hypothesis
  -> Workspace-scoped Calibration Projection
```

## Delivered

- immutable versioned property-state snapshots;
- temporal anti-hindsight selection using both effective and recorded time;
- versioned Reality Gap policies and deterministic classifications;
- explicit expected-minus-actual scientific error convention;
- evidence-backed human review of error hypotheses;
- calibration-candidate gate that cannot self-promote UNKNOWN causes;
- RealityCase composition over canonical records;
- workspace-isolated calibration projections;
- OpenAPI and AsyncAPI contracts;
- unit and PostgreSQL integration tests.

## Safety/scientific invariants

1. Snapshot != verified property fact.
2. RealityCase != source of truth.
3. Measured error != causal explanation.
4. Calibration candidate != automatic model update.
5. Information recorded after a decision cannot be treated as known before it.
6. Raw identifiable records are not pooled across workspaces.
7. External snapshot APIs cannot claim CANONICAL_FACTS or MIXED truth.
8. Human-reviewed non-UNKNOWN causal hypotheses require evidence.

## Verification gate

Wave 05 may merge only when the final branch head passes:

- Java 21 build;
- PostgreSQL 16/PostGIS Flyway V001 -> V010;
- Spring Modulith boundary verification;
- all existing Golden Path / Decision Intelligence / Reality Gap regressions;
- Wave 05 temporal and calibration tests;
- CodeQL Java analysis.

The objective is not autonomous self-learning. It is a trustworthy scientific
memory layer from which future validated learning can be built.
