# OULA Wave 04 — Reality Gap & Calibration Signal

## Goal

Close the first measurable loop between prediction and reality without allowing OULA to invent causes or silently recalibrate itself.

Current slice:

`Recommendation -> Human Decision -> Outcome -> RealityGap -> Calibration Signal`

## First production-safe metric

Wave 04 starts only with `commuteMinutes` because Wave 03 already persists both:
- expected commute for the selected recommendation alternative;
- observed commute from the real-world outcome.

The system therefore has a legitimate expected-vs-actual pair.

## Scientific conventions

- Operational variance remains: `actual - expected`.
- Reality Gap signed error is: `expected - actual`.
- Absolute error is `abs(expected - actual)`.
- Relative error is `absolute_error / abs(expected)` when expected is non-zero.

These definitions are explicit so analytics, research and product surfaces do not silently reverse signs.

## Error Genome seed

Every new gap starts with:

- `causeCategory = UNKNOWN_CAUSE`
- `reviewStatus = PENDING_REVIEW`
- `calibrationStatus = UNASSESSED`

Supported future cause taxonomy:

`DATA_ERROR | MISSING_VARIABLE | MODEL_ERROR | BEHAVIORAL_SHIFT | MARKET_SHOCK | REGULATORY_CHANGE | SPATIAL_CHANGE | EXECUTION_FAILURE | MEASUREMENT_ERROR | UNKNOWN_CAUSE`

OULA does **not** infer a cause from one error observation.

## Calibration safety rule

A Reality Gap is a calibration **signal**, not a model update.

Promotion path:

`UNASSESSED -> CANDIDATE -> CONSUMED`

or

`UNASSESSED -> EXCLUDED`

Only a later validation capability may promote a signal, and that capability must preserve evidence, model version, geography/time context and review authority.

## Traceability

Every Reality Gap is linked to:
- workspace;
- outcome;
- human decision;
- recommendation;
- exact model version;
- metric;
- expected and actual values;
- correlation ID;
- detection time.

## Production boundary

This wave intentionally does **not** implement:
- autonomous causal attribution;
- automatic LifeFit weight changes;
- Bayesian calibration;
- model retraining;
- cross-user learning;
- causal claims.

Those remain research/validation work until evidence justifies promotion.
