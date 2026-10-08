# OULA Engineering Wave 07 — Property Operations Execution Core

## Production loop

Guardian Action → Work Order → Human Approval → Provider Assignment → Start → Completion Evidence → Human Verification → Action / Obligation Resolution → Outcome.

## Delivered

- canonical work-order lifecycle tied to a Guardian Action;
- explicit human budget approval before execution;
- provider-party assignment without making OULA own external-provider identity truth;
- deterministic execution state machine;
- evidence reference required before completion review;
- budget guard: actual cost cannot silently exceed approved budget;
- completion verification resolves the linked Guardian Action only after review;
- Audit + Outbox coverage for every material work-order transition;
- dedicated work-order OAuth scopes under PROPERTY_MANAGEMENT purpose;
- database constraints and optimistic version checks;
- service/API integration coverage.

## Invariants

1. Action generation != external execution.
2. A Work Order is not a payment record.
3. Provider assignment does not imply provider verification.
4. Approved budget is a human-authority boundary.
5. Completion submission != completion verification.
6. Completion requires evidence reference.
7. An over-budget completion is rejected rather than silently accepted.
8. Only verified completion resolves the linked Guardian Action/Obligation.
9. All mutations are workspace-bound, auditable, idempotent at HTTP boundary, and event-backed.
10. Future payment/service-marketplace integrations remain provider-neutral adapters.

## Exit gate

Wave 07 may merge only after:

- Flyway V001 → V012 on PostgreSQL/PostGIS;
- all previous Golden Path, Decision, Reality Science and Guardian regressions;
- work-order lifecycle service test;
- work-order API authorization/idempotency test;
- Spring Modulith verification;
- CodeQL green.
