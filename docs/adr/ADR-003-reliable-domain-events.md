# ADR-003 — Reliable Domain Events

**Status:** Accepted

OULA uses versioned domain events, transactional outbox persistence, idempotent consumers and explicit correlation/causation metadata.

Event names describe facts that happened; events are not commands disguised as past-tense messages.
