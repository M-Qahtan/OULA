# OULA Cognitive Kernel — A1 / Scoped Planning & Hypothesis Laboratory
Date: 2026-10-20
Status: **BACKEND IMPLEMENTATION CANDIDATE** — deterministic and non-executing; not yet a generative agent, self-improving model, autonomous research worker or production service.

## Design doctrine
One OULA intelligence, many contextual experiences. This code augments **orchestration** and **intelligence** rather than creating a second property/market/decision source of truth. It preserves Person != User, Property != Listing != Building, Recommendation != Human Decision, Prediction != Observed Outcome and agent autonomy != legal authority. OULA and ROS remain completely separate.

## A1 contract
POST `/v1/agent/plans`; an authenticated JWT actor selects a workspace and exact purpose, authorized through `WorkspacePurposeAuthorizer` and existing `oula.experience.read` scope. The bounded `goal` is required, <=512 characters. This command performs only an in-memory calculation, has `Cache-Control: no-store` and does not echo the goal. It does not call a model, retain session text, query domain data, issue background jobs or execute transactions. It is not a general-purpose assistant endpoint.

Processing:
1. **UNDERSTAND:** NFKC case/Arabic-character normalization; coarse, deterministic goal classification; ask for clarification when not safely classifiable.
2. **BOUNDARY:** Detect explicitly high-impact signing/payment/ownership directives and return DENIED_ACTION without tool proposals. This keyword test is a *second-line UX rule, not a security control*.
3. **PURPOSE:** Require current independently authorized purpose. Context change is not permission escalation.
4. **PLAN:** Select catalog entries from pre-existing Java REST query endpoints. Mark whether the authenticated JWT has that tool's scope. A proposal only states `AVAILABLE_FOR_SEPARATE_REQUEST` or `MISSING_SCOPE`; it DOES NOT call or grant access. Each later real tool request must re-authorize actor + scope + workspace + purpose + domain.
5. **IMAGINE / 'DREAM':** Offer limited counterfactual hypotheses for housing, investment, asset care and deals. Every hypothesis is `UNVERIFIED_HYPOTHESIS` plus a falsification/evidence test, never scientific proof or a forecast.
6. **CRITIQUE:** Surface a list of missing evidence. No assumption is promoted into VERIFIED, no fabricated GIS, BIM, valuations, financing or registry readings.
7. **DECIDE:** Human authority and deterministic backend compliance retain all consequential decisions.
8. **LEARN:** Named but **NOT EXECUTED**. Future opt-in learning is evaluation against the existing RealityGap/Outcome apparatus; no continuous self-modifying prompts or unapproved retraining.

Worlds and release labels:
- PULSE: human intent/fit, draft rent–buy–wait hypotheses.
- PRISM: risk/investment and downside alternatives; no fabricated ROI.
- HABITAT: operations and maintenance hypotheses, diagnostic verification required.
- NEXUS: scoped deal *read* only and human-approved future actions.
- FORGE/CIVIC: `FUTURE_NOT_IMPLEMENTED`; no engineering/city simulation claim.

## Threat model
| Threat | A1 decision |
|---|---|
| Prompt injection granting itself admin role | User-provided text is never a tool name, identity claim, policy instruction or executable plan; scopes come from JWT authorities |
| Tenant/workspace confusion | Workspace, purpose and actor come from the existing Java authorizer; no user-supplied scopes |
| Agency outruns authority | No side effects or autonomous execution; high-impact commands denied; model adapter absent |
| Truth laundering | Hypothesis status is explicit; no retrieved records or fabricated VERIFIED facts |
| Private data / memory leakage | Goal not echoed in output, no new persistence, analytics or logging of prompt, no memory; do not store sensitive goals in client logs |
| Miscalibration | No invented confidence percentage or 'best model' claims |
| AI code leakage across ROS/OULA | Only the OULA repo and canonical modules |
| Resource spending | No paid model, cloud or external provider integrations in A1 |

## Why this is not yet a super-intelligent agent
No LLM-backed semantic understanding, retrieval-grounded synthesis, authorized tool execution, objective memory, real outcome-learning loop, model routing or model evaluation are connected. A keyword classifier with a hypothesis catalog cannot outperform leading models by itself. This A1 adds a **secure, testable control plane** before high-capability inference is granted access to property/user data.

## A2–A7 roadmap to real domain performance
A2. Integrate approved OIDC/BFF workspace (#123 candidate) then GET/canonical read-only tools with tenant/purpose-negative E2E tests and an independent audit.
A3. Implement `AgentToolRegistry` and governed orchestration: per-tool input/output contract, timeouts, budget, allowed data, consent, approval, idempotency/replay and protected logs; no arbitrary network/SQL.
A4. Model adapter and retrieval: multi-model routing by task; grounding in Property Passport and market evidence; citations, abstention and source checks. External model providers require an explicit founder-approved separate OULA API project, budget, privacy review and key-management plan.
A5. Memory and epistemic state: separation between consent-based user memory, workspace business state, immutable domain audit and hypothesis sandbox; per-actor workspace/purpose boundaries, retention, erase/revoke.
A6. Scenario generator, constrained optimization and adversarial critique for finance, architecture and city domain **only after verified, licensed tools** (BIM/structural/GIS) exist; human professional review.
A7. Scientific improvement/evaluation: offline replay of observed outcomes (RealityGap), train/eval splits, evaluator independence, benchmark versioning, rollback and release gate. No unsupervised self-training from unverified hypotheses.

## Benchmark specification — superiority is measured, never promised
A protected, independently curated Riyadh-first task suite, split by persona and risk:
- Goal understanding: semantic task accuracy by persona, ambiguity detection and abstention.
- Retrieval grounding: citation provenance, factual contradiction rates, stale/declared truth handling.
- Decision quality: constraint violations, regret under counterfactuals, outcome calibration; no causality claim from correlations.
- End-to-end success: authorization-correct completed tasks, P95 latency and cost per successful task.
- Safety: unauthorized tenant/purpose/approval attempts (target 100% denial), prompt-injection resistance, privacy leakage and reproducible adversarial red-teaming.
- Novelty: hypothesis usefulness and **independent falsifiability**, not verbal creativity or unsupported superlatives.
- Compare on the *same evidence, tools, budgets and task set* against chosen frontier models and ablated OULA components. No performance claim until a blinded evaluation report exists.

## Developer acceptance
- `mvn -B -ntp verify` with Java 21/PostgreSQL 16/PostGIS CI.
- Unit tests for plan purpose/scopes, prompt injection, future capability, material denial, no raw-goal echo, lexical Arabic/English examples.
- API/OpenAPI contract matching plus full negative JWT tests before real data integrations.
- Independent architecture/security review and PR approval required by `AGENTS.md`.
- Do not silently mark a `PLAN_ONLY` response as a live AI recommendation or a real tool action.
