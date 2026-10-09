# OULA Engineering Wave 12 — Property Vital Signs & Operational Outcome Core

## Mission

Give a managed property an auditable operational pulse without inventing a second source of truth or hiding judgment inside an opaque score.

## Production loop

Canonical Property / Operations / Provider Outcomes
→ deterministic Vital Policy
→ immutable Vital Snapshot
→ human-readable dimension states
→ domain event
→ future Reality Memory / calibration consumption.

## Vital dimensions in v1

1. **Obligations** — open and overdue canonical obligations.
2. **Guardian Risk** — open and critical Guardian signals.
3. **Execution** — active, overdue, completed and completion-review Work Orders.
4. **Cost Control** — actual cost relative to human-approved budget.
5. **Provider Outcome** — observed provider rating and quoted-vs-actual variance.
6. **Completion Evidence** — verified completion evidence coverage.
7. **Truth Coverage** — ratio of VERIFIED Property Facts to recorded Property Facts.
8. **Freshness** — age of the newest operational or property-fact observation.

Occupancy is deliberately not scored in Wave 12 because OULA does not yet own a canonical Lease/Occupancy truth model.

## Invariants

1. Vital Snapshot != Property Truth.
2. Vital Snapshot != AI inference.
3. No single opaque score hides the underlying metrics.
4. Every dimension is deterministic and bound to a versioned policy.
5. UNKNOWN is a valid state when evidence/data is absent.
6. Overall status is UNKNOWN when fewer than the policy-defined minimum number of dimensions are known.
7. Historical snapshots are immutable.
8. Policy thresholds are versioned; changing policy never rewrites prior snapshots.
9. Cross-workspace assessment is impossible.
10. Assessment may not execute external work, money movement or legal actions.
11. Vital events are suitable for later Reality Memory consumption but do not automatically retrain models.

## Exit gate

- Flyway V001 → V016 on PostgreSQL/PostGIS;
- Spring Modulith verification;
- all previous Golden Path / Reality / Guardian / Service Graph / Trust / Integration regressions;
- deterministic Vital classification integration test;
- immutable-snapshot database test;
- API purpose/scope/idempotency test;
- CodeQL green.
