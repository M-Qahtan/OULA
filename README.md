# OULA — Built World Intelligence & Operating Layer

OULA is built as one modular organism: a Real Estate Life Operating System that can grow into Built World Intelligence without replacing its operational core.

Human / Workspace → LifeGraph + Intent → Property + Spatial + Market Truth → Matching / LifeFit → Intelligence Recommendation → Human Decision → Transaction OS → Property Guardian + Work Orders → Service Graph + Verified Outcome → Reality Memory + Reality Gap → Evidence-backed Calibration Signals → Learning.

Cross-cutting trust path:

Actor / Agent → Purpose → Policy → Evidence / Verification → Approval when required → Authorized Action.

Current modules: iam, people, spatial, property, intent, market, matching, intelligence, decision, documents, transaction, operations, services, settlement, compliance, integration, orchestration, platform, and api.

Core invariants: Person != User; Property != Listing != Building; AI inference != Verified Fact; Recommendation != Decision; Simulation != Reality; Policy != executable prompt; Autonomous Authority != Human Authority; ROS is a future independent integration, never an OULA internal module.

Stack: Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, PostgreSQL 16, PostGIS, Flyway, OAuth2/OIDC resource server, GitHub Actions.

Quality gate: mvn -B -ntp verify

CI uses a real PostgreSQL/PostGIS service and checks migrations, module boundaries, authorization, idempotency, matching, transaction concurrency, API Golden Path, and the integrated OULA system body.

Architecture references:
- docs/architecture/OULA_SYSTEM_BODY.md
- docs/architecture/INTELLIGENCE_KERNEL.md
- docs/architecture/MODULE_BOUNDARIES.md
- docs/architecture/SECURITY_BOUNDARY.md
- docs/architecture/TRUST_COMPLIANCE_KERNEL.md
- docs/architecture/REALITY_MEMORY_SCIENCE_CORE.md

## Wave 05 — Reality Memory & Scientific Calibration
Immutable property-state snapshots, versioned Reality Gap policies, RealityCase lineage, evidence-backed human error-hypothesis review, and workspace-scoped calibration projections. A calibration signal is never an automatic model update.

## Wave 06 — Property Guardian & Management Core
Property Passport → Management Enrollment → Obligation → Guardian Signal → Action → Completion → Outcome.

## Wave 07 — Property Operations Execution Core
Guardian Action → Work Order → Human Budget Approval → Provider Assignment → Execution → Evidence → Human Verification → Resolution.

## Wave 08 — Service Graph & Settlement Boundary
Guardian → Work Order → Verified Provider → Capability → Quote → Human Selection → Execution → Verified Outcome → Settlement Reference.

## Wave 09 — Trust, Compliance & Autonomous Approval Kernel
Actor/Agent → Purpose → Deterministic Policy → Verification/Evidence → Human Approval when required → Re-evaluation.

The policy engine is fail-closed, uses exact-or-wildcard deterministic rules, records every decision, separates human authority from AUTONOMOUS_EXECUTION, and enforces Four-Eyes approval. Wave 09 establishes the reusable kernel; domain-by-domain enforcement wiring follows explicitly rather than guessing missing jurisdiction or financial context.
