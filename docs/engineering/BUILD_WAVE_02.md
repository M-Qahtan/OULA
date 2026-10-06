# Build Wave 02 — Living Core

This wave establishes the first executable cells of the OULA organism while keeping the Riyadh MVP narrow.

## Implemented in this branch
- Repository governance, CI and CodeQL.
- Spring Modulith boundaries.
- Flyway/PostGIS foundation.
- Explainable deterministic LifeFit domain engine.
- Intent aggregate invariants.
- Explicit transaction state machine.
- Intelligence Kernel contract hooks.
- API/Event contract baseline.
- Migration integration-test gate.

## Next vertical slice
\`Person -> Intent -> Candidate Retrieval -> LifeFit -> Shortlist -> Viewing -> Transaction\`

The next PR should add persistence/application services/controllers around the domain logic without breaking module ownership.
