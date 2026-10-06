# ADR-001 — Modular Monolith First

## Status
Accepted

## Decision
OULA starts as a modular monolith with strict Spring Modulith boundaries and event-driven integration. Modules can later be extracted using a strangler approach.

## Consequences
- Faster iteration before product-market fit.
- Strong transactional consistency inside aggregates.
- Requires architecture tests to prevent cross-module coupling.
