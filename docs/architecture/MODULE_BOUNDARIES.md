# OULA Module Boundaries

OULA starts as a modular monolith. Modules may collaborate only through explicit public seams. Direct cross-module table writes are forbidden.

## Current Wave 02 modules
- `intent` — structured intent; not a search query.
- `property` — property identity and truth-aware facts; not a listing.
- `matching` — candidate retrieval and deterministic explainable LifeFit.
- `transaction` — explicit transaction state machine.
- `platform.outbox` — reliable event-publication boundary.

## Invariants
1. `Person != User`.
2. `Property != Listing != Building`.
3. AI inference is never upgraded to a verified fact without evidence and authority.
4. Sensitive state transitions must carry actor, authority, evidence, audit, and event metadata.
5. Analytics and AI consume operational truth; they do not own transactional state.
