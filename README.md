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
Reality Memory + Reality Gap
      ↓
Evidence-backed Calibration Signals
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


## Current scientific learning boundary

Wave 05 adds immutable property-state snapshots, versioned Reality Gap policies,
RealityCase lineage, evidence-backed human error-hypothesis review, and
workspace-scoped calibration projections. A calibration signal is never an
automatic model update. See `docs/architecture/REALITY_MEMORY_SCIENCE_CORE.md`.


## Wave 06 — Property Guardian & Management Core

The operational lifecycle now extends beyond transaction completion:

`Property Passport → Management Enrollment → Obligation → Guardian Signal → Action → Completion → Outcome`.

Wave 06 keeps Property Passport as a projection over canonical property facts and observational state, while Property Guardian converts due operational obligations into auditable action items. It does not autonomously execute external legal, financial, or government actions.


## Wave 07 — Property Operations Execution Core

The Guardian lifecycle now has controlled execution muscles:

`Guardian Action → Work Order → Human Budget Approval → Provider Assignment → Execution → Evidence → Human Verification → Resolution`.

OULA does not equate a generated action with real-world execution, and does not treat a provider completion claim as verified completion. Work orders preserve approved budget, actual cost, evidence reference, actor, workspace, audit and domain-event lineage.
