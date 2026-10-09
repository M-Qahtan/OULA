# OULA Module Boundaries

OULA starts as a Spring Modulith modular monolith. Modules collaborate only through explicit public seams. Direct cross-module table writes are forbidden.

## Body modules
- iam — identity, workspace and purpose-aware authority.
- people — Person, Household and LifeGraph.
- spatial — place / built-world spatial ontology.
- property — asset identity and truth-aware facts; never a Listing.
- market — listing and market observations.
- intent — structured intent; never a search query.
- matching — candidate retrieval and deterministic explainable LifeFit.
- intelligence — Observation → Recommendation → Decision → Outcome contracts.
- decision — decision-case lifecycle.
- transaction — explicit transaction state machine.
- documents — evidence boundary.
- operations — Property Guardian, management and human-authorized Work Orders.
- services — Service Graph: operational provider profiles, capabilities, quotes and observed provider outcomes.
- settlement — provider-neutral references to externally executed settlements; never fund custody or movement.
- compliance — deterministic policy rules, decision ledger and independent approval lifecycle.
- integration — partner registry, purpose/data contracts, replay-safe ingress metadata, outbound preparation and provider-neutral adapter seams.
- vitals — immutable deterministic operational-health projections over canonical property/operations/service truth; never a second property truth store.
- advisory — read-only, explainable human-review recommendations from Vital trends and tenancy evidence; no execution, collection or approval authority.
- interventions — append-only human review and observed after-state lineage; not a finance, execution or causal attribution engine.
- tenancy — unit identity, lease terms, observed handovers, rent schedule and append-only renewal decisions; not a government registry or payment system.
- orchestration — coordinates whole journeys but owns no domain truth.
- platform — audit, idempotency, outbox and shared technical infrastructure.
- api — authenticated HTTP boundary.

## Invariants
1. Person != User.
2. Property != Listing != Building.
3. AI inference is never upgraded to a verified fact without evidence and authority.
4. Recommendation != Decision and Prediction != Outcome.
5. Sensitive state transitions carry actor, purpose, audit and event metadata.
6. Analytics and AI consume operational truth; they do not own transactional state.
7. No external vendor owns OULA's canonical meaning.
8. Provider profile != legal organization identity.
9. Quote != approval; Work Order != payment; Settlement Reference != fund movement.
10. Policy rule != executable code or LLM instruction.
11. No matching policy is a deny, not an implicit allow.
12. Autonomous execution authority != human property-management authority.
13. Vital Snapshot != Property Truth; UNKNOWN is preferable to a false-green low-coverage assessment.
14. Signed Lease != Physical Occupancy; Rent Due != Rent Paid; Renewal Acceptance != New Contract.
16. Documentary Rent Evidence != Bank-confirmed Settlement; Unknown Occupancy != Vacant Unit.
15. Operational Advisory != Authorization, Action, Diagnosis or Verified Outcome.
17. Rental advisory != debt determination, collection, eviction, or Guardian action execution.
18. Human advisory ACKNOWLEDGEMENT ≠ Compliance Approval; Intervention Outcome ≠ Caused Improvement.

19. Rental recommendation review is a distinct evidence track from operational
    Vital Signs intervention observations; no shared authority or rewritten truth.
20. An accepted human review is not execution approval, and documentary improvement
    observations are not counterfactual proof of recommendation efficacy.

21. Evaluation coverage != recommendation precision, causal impact, or system learning.
22. Missing evidence source != zero operational risk; zero-denominator rate is undefined.
23. Evaluation is an authorized read model only and cannot train models or rewrite domain facts.
