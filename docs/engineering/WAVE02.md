# Engineering Wave 02 — Golden Path

## Goal
Prove the first end-to-end OULA behavior for the Riyadh MVP:

`Intent -> Candidate Retrieval -> LifeFit -> Decision -> Transaction -> Outcome`

## Foundation now established
- Spring Boot/Spring Modulith project foundation.
- explicit module boundaries.
- deterministic hard-constraint retrieval.
- explainable LifeFit v1.
- persistent MatchRun + ranked MatchResult records.
- explicit transaction-state invariants.
- optimistic concurrency for transaction transitions.
- PostgreSQL/PostGIS schemas for workspace, intent, property truth, matching, transaction, outbox, idempotency, and audit.
- CI migration/integration gate against PostgreSQL + PostGIS.

## Current integrity rules
- A MatchRun is written transactionally and completes only after all ranked results are persisted.
- Duplicate correlation IDs are rejected per workspace.
- Transaction state changes require the caller's expected aggregate version.
- Illegal state transitions are rejected before persistence.
- Stale transaction writes cannot silently overwrite a newer state.

## Not yet claimed complete
REST APIs, OIDC/workspace authorization, idempotent HTTP command handling, outbox transport, and Golden E2E API tests remain subsequent slices.
