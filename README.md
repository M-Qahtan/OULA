# OULA — Built World Intelligence & Operating Layer

OULA is built as one **modular organism**: a Real Estate Life Operating System that can grow into Built World Intelligence without replacing its operational core.

```
Human / Workspace
      ↓
LifeGraph + Intent
      ↓
Property + Spatial + Market Truth
      ↓
Matching / LifeFit
      ↓
Intelligence Recommendation
      ↓
Human Decision
      ↓
Transaction OS
      ↓
Operations / Outcome
      ↓
Learning
```

Current modules: `iam`, `people`, `spatial`, `property`, `intent`, `market`, `matching`, `intelligence`, `decision`, `documents`, `transaction`, `operations`, `compliance`, `integration`, `orchestration`, `platform`, and `api`.

Core invariants: Person != User; Property != Listing != Building; AI inference != Verified Fact; Recommendation != Decision; Simulation != Reality; ROS is a future independent integration, never an OULA internal module.

Stack: Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, PostgreSQL 16, PostGIS, Flyway, OAuth2/OIDC resource server, GitHub Actions.

Run the quality gate with:

```bash
mvn -B -ntp verify
```

CI uses a real PostgreSQL/PostGIS service and checks migrations, module boundaries, authorization, idempotency, matching, transaction concurrency, API Golden Path, and the integrated OULA system body.

Architecture references:
- `docs/architecture/OULA_SYSTEM_BODY.md`
- `docs/architecture/INTELLIGENCE_KERNEL.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/SECURITY_BOUNDARY.md`
