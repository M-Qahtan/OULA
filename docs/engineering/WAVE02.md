# Engineering Wave 02 — Golden Path

## Goal

Prove the first end-to-end OULA behavior for the Riyadh MVP:

`Intent -> Candidate Retrieval -> LifeFit -> Decision -> Transaction -> Outcome`

## Implemented foundation

- Java 21 + Spring Boot + Spring Modulith.
- explicit module boundaries verified by test.
- PostgreSQL + PostGIS with Flyway migrations.
- deterministic hard-constraint candidate retrieval.
- explainable LifeFit v1.
- persistent MatchRun + MatchResult.
- explicit transaction state machine.
- optimistic transaction concurrency.
- UUIDv7 persisted identifiers.
- OAuth/OIDC Resource Server boundary.
- workspace + purpose + OAuth scope authorization.
- idempotent HTTP command ledger.
- deterministic retry correlation using UUIDv8.
- transactional outbox writer.
- concurrent-safe outbox dispatcher using `SKIP LOCKED`.
- REST boundary for matching and transaction progression.
- Golden Path API integration tests.

## Integrity rules

- `Person != User`.
- `Property != Listing != Building`.
- AI inference never becomes verified truth automatically.
- headers declare context; JWT claims authorize it.
- Purpose is independent from role.
- stale writes cannot silently overwrite newer transaction state.
- retries cannot mutate a completed command under the same idempotency key.
- important domain changes emit outbox events in the same transaction.
- commute is not invented as a property fact; intent-specific commute is stored as a sourced matching signal.

## Remaining after this wave

The next engineering wave should focus on:

1. production IdP adapter/configuration and key-rotation operational tests;
2. durable external event transport only when an integration requires it;
3. Audit Writer linking actor + subject + purpose + correlation to sensitive commands;
4. Decision Intelligence objects: Evidence, Assumption, Recommendation, DecisionRecord, Outcome;
5. first end-to-end Outcome learning loop.
