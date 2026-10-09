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


## Wave 10 — Policy Enforcement Integration

The Trust Kernel now sits inside material domain mutations, not only at the HTTP boundary.

Policy-gated operations:
- Work Order budget approval;
- provider assignment;
- Work Order start;
- Settlement Reference recording.

PROPERTY_MANAGEMENT remains the human authority path. AUTONOMOUS_EXECUTION can reach only these policy-gated seams and must satisfy the active rule, including exact-context human approval when required. Completion submission and completion verification remain human-only in Wave 10.


## Wave 11 — Integration Trust Fabric

External integrations now enter OULA through a provider-neutral trust seam:

Transport Adapter → VerifiedExternalPrincipal → Verified Partner → Integration Contract → Purpose / Operation / Resource / Data-Class Check → Replay Guard → Metadata Receipt.

The ingress port does not expose a raw public webhook. Concrete mTLS/JWS/signed-webhook/OAuth adapters must authenticate transport first and may then construct VerifiedExternalPrincipal. Raw inbound payload is not stored in the integration receipt; OULA records hash/reference metadata and emits a trusted domain event.


## Wave 12 — Property Vital Signs & Operational Outcome Core

OULA now derives an auditable property pulse from canonical operational truth:

`Obligations + Guardian Risk + Work Execution + Cost Control + Provider Outcomes + Completion Evidence + Truth Coverage + Freshness → Versioned Vital Snapshot`.

Each dimension is deterministic and explainable. `UNKNOWN` is a first-class state, and the overall status remains UNKNOWN when fewer than the policy-required number of dimensions are supported by evidence. Vital snapshots are immutable derived observations; they never become Property Truth automatically.

## Wave 13 — Lease, Occupancy & Tenancy Truth

Property → Unit → Draft Lease → Verified Contract Evidence → Signed Lease → Active Lease
→ Scheduled Rent (not payment truth) → Verified Handover → Recorded Occupancy
→ Renewal Decision (not automatic extension) → Verified Check-out → Evidence-backed End.

An internal signed lease is not a government-registered e-contract, a rent installment is not proof of payment, and a signed lease never proves physical occupancy. Unit occupancy shows UNKNOWN until handover records exist. Lease overlap is rejected at PostgreSQL level.
