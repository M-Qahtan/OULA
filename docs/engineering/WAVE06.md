# OULA Engineering Wave 06 — Property Guardian & Management Core

## Production loop

Property Passport -> Management Enrollment -> Obligation Ledger -> Guardian Assessment -> Signal -> Action Item -> Completion -> Operational Outcome / future Reality Memory.

## Delivered

- purpose-aware Property Passport projection;
- explicit separation of verified/declared/inferred property facts;
- active property-management enrollment;
- canonical obligation ledger;
- deterministic Guardian due-date assessment;
- idempotent Signal and Action generation per obligation;
- executable Action completion that resolves its linked obligation and Guardian signal;
- Audit + Outbox coverage for material management transitions;
- PROPERTY_MANAGEMENT authorization purpose and dedicated scopes;
- PostgreSQL constraints/indexes for lifecycle integrity;
- service and API integration tests.

## Invariants

1. Property Passport is a projection; it does not create a second property source of truth.
2. Observation != verified fact.
3. Guardian detects operational conditions; it does not silently execute external legal/financial actions.
4. Action generation != external execution.
5. An obligation remains canonical operational state.
6. Cross-workspace management is denied.
7. Every material lifecycle transition is auditable and event-backed.
8. Future autonomous execution must pass compliance, authority and explicit approval gates.

## Exit gate

Wave 06 may merge only after:
- Flyway V001 -> V011 on PostgreSQL/PostGIS;
- Spring Modulith boundary verification;
- all previous Golden Path / Decision Intelligence / Reality Science regressions;
- Property Passport truth-status test;
- Guardian idempotency and completion lifecycle test;
- management API authorization test;
- CodeQL green.
