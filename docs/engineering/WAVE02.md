# Engineering Wave 02 — Golden Path

## Goal
Prove the first end-to-end OULA behavior for the Riyadh MVP:

`Intent -> Candidate Retrieval -> LifeFit -> Decision -> Transaction -> Outcome`

## This foundation establishes
- Spring Boot/Spring Modulith project foundation.
- explicit module boundaries.
- deterministic hard-constraint retrieval.
- explainable LifeFit v1.
- transaction-state invariants.
- PostgreSQL/PostGIS schemas for workspace, intent, property truth, matching, transaction, outbox, idempotency, and audit.
- CI migration smoke gate.

## Not yet claimed complete
REST APIs, OIDC/workspace authorization, persistent MatchRun application services, optimistic-concurrency wiring, outbox transport, and Golden E2E API tests remain subsequent slices.
