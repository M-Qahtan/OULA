# OULA Calibration Readiness Gate — Wave 22

## Mission

Move OULA from descriptive decision-evidence coverage toward scientifically
controlled calibration readiness without claiming predictive validity, causal
effect or autonomous learning.

The read-only chain is:

```
Reality Gap projections
  -> Review coverage
  -> Evidence-backed calibration candidates
  -> Unknown-cause exposure
  -> Readiness report
  -> Research protocol requirements
  -> Separate validation gate
```

## What Wave 22 does

- aggregates workspace-scoped calibration projections for one ModelVersion;
- preserves metric policy/version lineage;
- reports review coverage and calibration-candidate coverage;
- keeps unknown causes explicit;
- names the missing scientific prerequisites before any validity/effect claim;
- exposes the report through a no-store, purpose-bound endpoint.

## What it deliberately does not do

- no automatic model retraining;
- no weight update or ModelVersion mutation;
- no causal attribution;
- no accuracy certification;
- no cross-workspace pooling;
- no hidden minimum-sample threshold;
- no production promotion of a calibration candidate.

A calibration candidate means only that a Reality Gap passed the existing
human evidence review into candidate status. It is not an approved model change.

## Endpoint

`GET /v1/intelligence/models/{modelVersionId}/calibration-readiness`

Purpose: `PROPERTY_DECISION_SUPPORT`  
Scope: `oula.intelligence.read`

## Exit condition

OULA can answer, for one workspace and ModelVersion:

> What observed error signals exist, how much has been human-reviewed, how much
> is evidence-backed enough to be a calibration candidate, what remains unknown,
> and what scientific controls are still required before validation or model change?

That answer remains descriptive and auditable.
