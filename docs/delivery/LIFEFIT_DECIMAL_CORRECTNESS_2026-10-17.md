# LifeFit decimal and locale correctness

Tracking: #113. Baseline: main@c945e1346f292809e6e5d9e0a4e43ec76db6ff7a after #110.

## Reproduced defects and bounded correction

- With budget 100000 and asking price 85005.01, v1 reports financial=100.00 because utilization is rounded to six decimals before scoring. The decimal implementation reports 99.99. A local standalone Java probe fails on baseline (expected 99.99, got 100.0) and passes on this change.
- JSON explanation formatting used the host default locale. A comma decimal locale produces invalid JSON for PostgreSQL jsonb. Use Locale.ROOT; regress with US/German/Arabic host settings and a real DB insertion under German FORMAT locale.
- LifeFitResult accepted NaN because ordered comparisons are false for NaN. Reject all non-finite score/confidence/dimension values and invalid ranges.
- Direct calculator calls now reject cross-workspace candidates, in addition to existing retrieval filtering.

Financial numerators and weighted sums use BigDecimal. Division uses DECIMAL128 (34 significant digits); final public scores/dimensions/confidence use two decimal HALF_UP rounding. This is bounded numerical precision, not absolute accuracy. No weights, hard eligibility filters, ranking tie-breaker or confidence heuristic policy are changed. Financial utility retains the existing discontinuity above budget; normal retrieval excludes over-budget candidates.

## Provenance and compatibility

New runs/outbox messages use lifefit-v2-decimal. V028 adds an ACTIVE model runtime mapping with explicit uncalibrated limitations; old v1 model rows and historical records remain. Recommendation API regressions assert v2-decimal; existing recommendation explanation derives its basis from the recorded run version. The legacy direct kernel emits the current version. No API shape change.

FACT means recorded source data, not validated market truth. LifeFit scores are INFERENCE and outputs remain RECOMMENDATION requiring existing human authority. The confidence field is still a coverage heuristic, not calibrated probability or legal/financial certainty. This change does not upgrade DECLARED facts or evidence verification. Source authority validation and evidence eligibility need a separate audited change.

## Verification

- Local standalone Java compile + regression and 1000 seeded scale-invariance cases passed. Full Spring tests are delegated to existing Java 21/PostGIS CI; local Maven/Java 21 runtime is unavailable.
- Added 1000 seeded metamorphic cases covering amount scaling, non-increasing price/commute utility, non-decreasing truth coverage and bounded output; negative/null/cross-workspace, extreme amount/commute and rounding boundary tests.
- DB regression verifies model mapping, matching run and outbox version agreement; existing API recommendation tests and full browser Golden Path exercise downstream compatibility.
- Required before merge: Java/PostGIS, Frontend, Browser Integration, CodeQL all successful on exact final head, plus independent review.

No production calibration, external provider connection, deployment or field acceptance is claimed. ROS is untouched. Revert code to restore v1 for future runs; retain additive V028 and historical v2 records for audit rather than deleting migration history.
