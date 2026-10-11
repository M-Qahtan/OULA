# OULA Personal Agent Runtime — P0 Foundation (2026-10-11)
Status: **IMPLEMENTED AS DEMO / DESIGN SEAM; NOT LIVE AI OR AN AUTHENTICATED AGENT**.

## Executive decision
Give the user one governed presence, not one disconnected chatbot per product. The six experience worlds are *presentation lenses over the current canonical domain body*; they do **not** own truth, credentials, payments, policy or workflow state. OULA is independent of ROS.

### Experience-world → authoritative organ projection
| World | View in P0 | Existing body organs | P0 capability |
|---|---|---|---|
| PULSE | Intent | people, intent, matching | Guided local navigation to demo LifeGraph Lite |
| HABITAT | Guardian | operations, vitals, advisory | Guided local navigation to demo property care |
| PRISM | Decision | market, matching, intelligence, decision | Guided local navigation to demo human decision |
| NEXUS | Deal Room | transaction, compliance, documents | Explicit acknowledgment before demo navigation; no deal mutation |
| FORGE | None | Future engineering/development, no live engine | FUTURE; no fabricated engineering model |
| CIVIC | None | spatial and future city intelligence | FUTURE; no fabricated urban/digital-twin calculations |

The current canonical screen journey remains home → intent → matching → passport → decision → deal → guardian → intelligence. The world lens does not rename or supersede the backend bounded contexts. No direct module access from the browser.

## Code in this slice
- `apps/web/lib/personal-agent.ts`: deterministic P0 request classification, allowlisted world-to-stage route projection, hard block on regulated commands, future/world status vocabulary.
- `apps/web/components/oula-personal-agent.tsx`: accessible AR/EN local agent dock with six world buttons, optional typed goal, visible steps, pause/resume, session trace, clear and review acknowledgment for demo Deal Room.
- `apps/web/components/oula-experience.tsx`: the dock is mounted inside the canonical Next.js frontend, with an explicit navigation callback; no iframe, simulator takeover or extra frontend repository.
- `apps/web/app/globals.css`: responsive Cognitive Halo, including reduced-motion respect and accessible focus styles.
- `apps/web/tests/personal-agent.test.ts`: fail-closed behavior tests and world-mapping contract.

## Scope and authority
- No LLM or external AI model is invoked by this P0. Keyword routing is deterministic, restricted and explained as local.
- This P0 is **not** a backend tool-executing Agent Runtime. The existing browser has no authenticated OAuth/OIDC session (#46/#41); therefore no Java API actions are represented as already executed.
- No browser secret, bearer token, persistent profile, session-storage/local-storage memory, payment or contract-signing capability is introduced.
- The visible session trace is **volatile in React state**, not durable canonical Audit. Clearing it destroys the local view. Browser reload discards it.
- Demo acknowledgment is never a legal signature, contract approval, authorization, delegation or consent to process protected data.
- The backend remains the exclusive truth holder. IAM enforces subject + actor + workspace + purpose + scope; compliance owns deterministic policy and explicit approvals; intelligence proposes; orchestration coordinates but does not own facts; transaction owns committed state; settlement only references external settlement.
- P0 text requests are **not sent** to server, AI or analytics by this component. There is no remote tool access. Unsupported user goals result in CLARIFY, not made-up answers.
- This P0's regular-expression screening is UX gating only. It is **not a security boundary**, and it must never substitute for a backend permission check in P1.

## Planned agent runtime under existing body (P1 — NOT IMPLEMENTED HERE)
1. **Browser session, consent and authority**: close #46. Use an authenticated, approved server boundary; no client-side secrets; agency is on-behalf-of and does not inherit human authority.
2. **Tools and model governance**: versioned registry; per-tool required purpose/scope; step budgets/timeouts; allowlisted server-side tools; input/output validation; safe model routing, prompt registry, eval and cost ceilings. Do not permit arbitrary LLM-selected routes, raw SQL, direct cross-module writes or client-supplied role claims.
3. **Execution**: orchestration coordinates user-approved goals through existing domain APIs only; no independent canonical agent database duplicating people, property, transaction or compliance.
4. **Explicit authority gate**: preview → propose → approval challenge tied to actor, workspace, purpose, target, operation, expiry and idempotency key → authorized Java command → server audit/outbox. Deny by default and reject replay, stale versions, wrong tenancy, wrong purpose or revoked consent.
5. **Agent trace and cancellation**: durable, scoped, privacy-appropriate audit of plan/observations/decisions/tool calls, distinct from optional user memory. Pause/cancel cannot roll back already committed authorized domain transactions; show actual effect honestly.
6. **Reality check**: verify field provenance; distinguish OBSERVED/DECLARED/VERIFIED from INFERRED; accept a canonical returned outcome only when proven. Never downgrade backend faults into unmarked DEMO.
7. **P1 read-only acceptance**: browser session → explicit user intent → approved `createIntent` or `getIntent` (when actually authorized) → `runMatching` → `getPropertyPassport` → `generateRecommendation` and explainability, with backend IDs and persisted evidence; negative authorization and workspace tests required. Any non-read-only action needs a server-side authority decision.
8. **Later P2**: agent-initiated viewings/offers/deal orchestration only after backend transaction creation and commands exist (#39/#49), then Guardian lifecycle. FORGE/CIVIC stay research until real domains and evidence are available.

## No silent promotion
Do not mark a world or a step LIVE merely because the P0 preview renders or local tests pass. Follow `docs/delivery/FRONTEND_BACKEND_CONTRACT.md` and `docs/delivery/frontend-backend-contract-matrix.json`; publish authenticated end-to-end evidence, CI, review and adversarial safety tests before labeling any production capability.
