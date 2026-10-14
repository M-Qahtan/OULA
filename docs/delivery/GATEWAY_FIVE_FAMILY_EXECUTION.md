# OULA Five-Family Gateway — design + delivery contract v1.0
**Decision:** 12 Oct 2026 | **Parent:** #38 | **Gateway:** #75 | **Design:** #51 | **Integration:** #41/#52 | **AI Runtime:** #71

## One operating organism; five user-facing families
The founder approved **five** gateway families: PULSE/نبض, HABITAT/موئل, NEXUS/وَصْل, FORGE/هندسة, CIVIC/مدن. The prior P0 personal-agent module refers to six *internal* thematic worlds, including PRISM. **Do not delete or silently rename PRISM in existing runtime/test contracts**: its investment/decision capability remains internal to shared intelligence and may be presented contextually through Habitat/Nexus/Pulse as permitted. UI taxonomy is not a permission or domain-ownership model.

## Definitive device behavior
- **Desktop >= 1024px:** cinematic five-arch OULA master gateway; connected visual family, OULA master ring above; agent below. The original visual concept is an art-direction reference, not a background screenshot or static navigation.
- **Phone <= 767px:** master OULA ring with **five circular family glyphs** in 2+2+1 layout; no giant architecture assets, 3D engines or compulsory animations. Same branch semantics and agent entry.
- **Tablet:** adaptive layout; physical breakpoint may change after real viewport testing. Semantic accessibility survives regardless of screen size.
- English/Arabic share the same source taxonomy and equivalent task semantics. RTL/LTR and focus visible must work.
- Motion must respect prefers-reduced-motion.

## Source-of-truth boundary
The gateway.ts module contains PUBLIC PRESENTATION metadata only. Do not use it to determine user roles, permissions, property truth, or backend capability. **All worlds are labelled PREVIEW** until a real authorized backend operation satisfies the canonical source-of-truth contract in docs/delivery/FRONTEND_BACKEND_CONTRACT.md.
- Existing authoritative server: Spring Boot/Modulith + PostgreSQL/PostGIS.
- Existing web: Next.js/React/TypeScript and the eight-stage Golden Path demo at the root.
- /gateway is an opt-in nonbreaking route during refinement. Replace root only under a separately reviewed routing/acceptance PR.
- /gateway/pulse, /gateway/habitat, /gateway/nexus, /gateway/forge, /gateway/civic now each have their own responsive *preview* task shell. One portal activation opens its family route. These shells are navigable, localized and clearly state demo/future readiness; they do not create five independent backends or grant roles.
- The public five-family taxonomy maps to specialized tasks, but backend authority and actual capabilities must later come from authenticated Adaptive Experience Manifest (#74), not static frontend configuration.
- Current gateway assistant is suggestWorld: limited deterministic keyword routing; no LLM, server tool-call, personal memory, consent or external actions.
- The gateway links into the existing clearly marked local demonstration journey, not five imaginary domain backends.

## Integration handoff, in dependency order
1. **#46 IAM / #74 Adaptive Experience:** authenticated user-session and workspace purpose/scope; until it passes, fail closed.
2. **#47/#48/#49 Backend Golden Path:** property facts, match, viewing/offer/deal states and reliable Riyadh seed.
3. **#41/#52 Frontend integration:** typed transport from canonical contracts/openapi.yaml, real server errors surfaced without silent demo fallback.
4. **#71 Governed Personal Agent:** agent reads authorized context, differentiates proposal/action, and uses human confirmation for regulated acts.
5. **#42 Independent QA:** 390/1024/1920 AR/EN and keyboard/reduced-motion performance and security; no false claims.
6. **#53 Local reproducibility:** VS Code local runbook; no cloud/domain/payment procurement or production deployment.

## Role-based execution ownership
- oula-chief-architect: contract acceptance and five-family vocabulary.
- oula-frontend: semantic responsive gateway and branch shells.
- oula-backend: preserve domain truth & APIs.
- oula-integration: exact typed API+UI transitions and authorized session.
- oula-platform: identity, workspace, purpose, session; zero-cloud local operations.
- oula-qa-security: independent negative/security/accessibility checks.
- oula-product-science: separate evidence-backed intelligence from simulated/future capability.

**These are workstream owner roles; agent profile definitions alone are NOT autonomous running workers.** Assignment/activation must be verified before claiming an agent is executing.

## Definition of Done
PR review + reproducible TypeScript, Vitest, Next build, Playwright across target viewports, Java contract compatibility, CodeQL where applicable. A screenshot/mockup alone is not product readiness. Do not merge into main or deploy before passing gates and review. Keep known limitations visible and issues linked.

## Next acceptance experiment
Ask five participants unfamiliar with OULA to find (a) a home, (b) manage property, (c) arrange a brokerage deal, (d) review an engineering project, (e) assess a city policy. Measure correct-first-click, task completion, explanation requests, navigation confusion and perceived trust. The target is measured usability rather than a fabricated 100/100 score.
