# ADR-003 — Versioned Domain Events

## Status
Accepted

## Decision
Events use `<domain>.<aggregate>.<event>.vN`, an immutable envelope, and transactional outbox publication. Consumers are idempotent.
