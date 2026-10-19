# OULA Main Workspace UX Contract

## Product context
Arabic RTL and English LTR; Riyadh-first global architecture; consumer, owner, manager, broker, investor, lender, engineer, developer and government views. The founder's supplied images ground visual direction in `DESIGN.md`. Date-time values retain server provenance; local UI dates use the workspace context. Accessibility targets WCAG 2.2 AA.

## Authoritative sources
`../../contracts/openapi.yaml`, `../../docs/delivery/FRONTEND_BACKEND_CONTRACT.md`, `../../docs/architecture/SECURITY_BOUNDARY.md`, `../../AGENTS.md`, and Java controllers under `../../src/main/java/com/oula/api` govern data and permissions. The current task authorizes role-shaped main interfaces and frontend/backend integration. Rendering a future workspace does not approve an engineering/city execution engine.

## Canonical UI Map
| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Select/Listbox | Native HTML select in shared workspace and connected forms | DESIGN.md | native, platform-owned popup | workspace browser tests and keyboard checks |
| Date | Native datetime-local input in ConnectedDealRoom | API + this contract | native, platform-owned picker; device-local entry, explicit Riyadh display | integration and browser checks |
| Form | ConnectedJourney + API-contract schemas | OpenAPI + this contract | intent / record read / decision | bridge and browser integration tests |
| Scrollbar | application global stylesheets | DESIGN.md | main dark workspace / existing light demo | computed styles and mobile overflow checks |
| Toast | StatusBanner persistent live regions | this contract | info / error / success | browser failure and decision checks |
| CRUD | browser-api -> finite server bridge -> Java API | OpenAPI | explicit supported mutations / reads | contract harness full flow, negative tests; Java DB acceptance remains separate |

## Consistent outcomes
Workspace navigation preserves non-sensitive world, role, view and locale in the URL and restores browser Back. The main heading receives focus after route/view changes. User-entered goals, criteria, evidence and tokens never enter navigation URLs or persistent browser storage. Assistant suggestions navigate only on an explicit user action. Changing role/world/data mode or authorized workspace discards the previous presentation dataset to prevent mixing identities and fixtures.

Intent submit validates input before sending and prevents concurrent submission. A command retains the same idempotency key on a semantically identical retry. Matching retries reuse a successfully created intent, rather than writing another intent. Recommendation and decision retries retain the command key and successful preceding result. A 409 remains visible; starting a new command is explicit. There is no automatic write retry, state advance or LIVE-to-DEMO fallback.

Successful intent/matching opens results; successful passport read opens the passport; a successful human decision displays its returned ID. Recording a decision does not sign, pay or transfer ownership. Deal Room supports draft creation, qualification, viewing requests, offers and reads through the canonical Java contracts. Every material step has an explicit human confirmation, stable retry key and expected version. Guardian reads the latest vitals and operational advice independently; absent/failed advice does not turn a snapshot into fabricated advice.

## Session and security presentation
OIDC Authorization Code with PKCE/state/nonce and validated signed tokens creates a server-protected encrypted HttpOnly cookie. The browser sees a public session projection only. Workspace switching accepts only verified token memberships. Role selection is a view preference and grants nothing. The finite BFF selects purpose/scope from canonical endpoint contracts, ignores browser-forged identity headers and denies cross-origin mutations. Logout clears local session credentials; provider-wide SSO logout/revocation is not claimed.

The session cookie lasts at most one hour and never outlives the access token. No automatic refresh is implemented. Invalid/expired sessions require sign-in; absent configuration is shown explicitly. Configuration permits HTTP only for explicitly enabled literal loopback services. This release is configurable local integration, not a certified production identity deployment.

## Dataset and failure behavior
The preview uses three existing fictional properties and local bounded matching. Workspace results come only from service responses checked against runtime schemas. Property evidence statuses, JSON-text values, unknown currency and coverage semantics remain server-provided. Catalog results are bounded by the contracted matching response; no unbounded infinite scrolling is introduced. Loading disables affected actions. Empty/no-results screens lead back to intent. Authentication, permission, missing record, validation, conflict, unavailable service and contract mismatch have separate error states. Inputs remain in memory for retry; sensitive drafts are not silently persisted.

## Verification
Run `npm run typecheck`, `npm test`, `npm run build`, `npm run test:e2e`, and the frontend-design-premium strict audit. Browser evidence covers 1920x1080, 1024x768, 390x844, RTL/LTR, keyboard, reduced motion, empty/error/success states and cookie-authenticated OIDC/BFF flow through a clearly synthetic contract harness. That harness is not the canonical Java/PostgreSQL path. `npm run test:integration` additionally exercises signed Java development identity, encrypted BFF cookies, buyer commands and HABITAT property/listing read-backs against PostgreSQL. Root release gates and independent maintainer review remain required before merge/production claims.

## Spatial workspace extension — 19 October 2026

The following extension changes visual, responsive and cache ownership. The authority, outcomes, privacy and failure contracts above continue to apply.

## Business evidence

| Rule | Authoritative source | UI consequence |
|---|---|---|
| Scope, purpose and workspace authority | `lib/server/bridge.ts`, `contracts/openapi.yaml`, Java authorizers | A selected role only changes presentation; rejected commands never appear completed. |
| Transaction lifecycle and human confirmation | Java transaction services, `lib/api-contracts.ts` | A draft, viewing request and submitted offer use their exact server states; approval precedes each command. |
| Evidence and truth | `PropertyPassport`, `RecommendationExplanation` contracts | Source labels and unknown fields remain visible. Coverage is not ownership proof or confidence. |
| Session isolation | `lib/session-state.ts`, `use-oula-session.ts` | Account/workspace/purpose/scope changes unmount the private journey, cancel its reads and clear its in-memory QueryClient. |
| Same command retry | Existing BFF idempotency contracts | Identical retries retain the command key; conflicts stay visible. |

## Canonical UI map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Navigation | `components/spatial-shell.tsx` | URL world/view/task context | Desktop sidebar / mobile floating hub | `e2e/spatial.e2e.ts` |
| Drawer | `SpatialShell` native dialog | Native modal focus/inert/Escape | Persistent nonmodal desktop / modal tablet/mobile | `e2e/spatial.e2e.ts` |
| Select/Listbox | Existing labeled native `<select>` | Native platform popup | Native; platform geometry deliberately retained | `e2e/workspace.e2e.ts` |
| Date | Existing `datetime-local` controls | Device local entry, UTC API, Riyadh display | Native; OS calendar locale/geometry accepted | `e2e-integration/workspace.integration.ts` |
| Form | Existing domain forms + Zod schemas | Runtime request contracts | Explicit save and human approval | `e2e/workspace.e2e.ts` |
| Scrollbar | `styles/spatial.css` | Runtime tokens | Global application baseline, forced colors system override | `e2e/spatial.e2e.ts` |
| Toast | `StatusBanner` in `workspace-primitives.tsx` | Persistent scoped status | Success/error/info; no toast-only failures | `e2e/workspace.e2e.ts` |
| CRUD | `ConnectedJourney`, `ConnectedDealRoom`, `ConnectedPropertyCare` | Existing Java/BFF commands | Read back after each confirmed write; no invented delete | `e2e-integration/workspace.integration.ts` |
| Cache | `lib/workspace-query.tsx` | Cookie BFF typed reads | Memory only; 20s reads; explicit refresh and writes always fresh | `e2e/spatial.e2e.ts`, session-continuity tests |

## Shared state and interaction

1. All five worlds use the same shell, type scale, scene, glass panels, controls and keyboard behavior. Deal room and decision views use amber; world palettes retain their own hue.
2. At 1280px and above, navigation/canvas/assistant occupy three columns. At 768–1279px the assistant opens in a modal drawer. Below 768px the main navigation becomes a floating bottom hub with safe-area padding. The document owns page scrolling; long forms remain reachable.
3. The drawer keeps one assistant subtree, traps focus through native `showModal`, restores the trigger on Escape/close, and locks background document scrolling. Resizing to desktop restores a persistent nonmodal panel. No duplicate assistant inputs exist.
4. Navigation, world switches, display layers and slider values update immediately. Material writes enter a pending state with a stable skeleton and disabled duplicate triggers. A server-confirmed read-back is required before showing a saved stage. Network errors do not advance business state.
5. A QueryClient lives only within the existing identity/authority-keyed private journey. No sensitive cache is persisted in storage. Explicit reload removes cached reads; command read-backs request fresh data. No automatic mutation retries or invented WebSocket connection are added.
6. Public family discovery remains finite and parent-validated. Thirty links target existing workspace forms, and 60 planned branches have no execution action. Scenic renders and presentation layers are labeled illustrative; they carry no operational statistics or simulation results.
7. Source-linked fact lists initially render six facts and explicitly load six more. Current transaction command history is rendered from the bounded journey response; server paging is required before a future unbounded ledger.
8. Arabic RTL and English LTR own copy and form direction; the physical desktop navigation remains left and assistant right. IBM Plex Sans Arabic and DM Sans are bundled. Mixed identifiers use LTR direction; date displays specify Riyadh time. Unknown currency stays unknown.
9. Motion respects reduced-motion preferences. Semantic controls preserve labels and visible focus. Errors and pending/saved states use text, not color alone. Missing data stays empty rather than becoming example metrics.

## Migration and rollback

The existing stateful forms, protected BFF and server contracts remain owners. The old family stylesheet is retired; both route families consume `styles/spatial.css`. The original main gateway stays the visual reference. Revert the spatial UI commits to roll back; no database migration is involved.


## Interactive visual layer — 19 October 2026

The 3D renderer is progressive decoration. Its loading, quiet, failed and interactive states never gate record reads, authorization or business commands. The shared environment owns local rotate/zoom/reset controls, localized names, a visual-only retry and native touch-scroll preservation. Rendering state does not enter the private journey authority key or persist in browser storage.

A global ambient-motion control pauses CSS light orbs, shader time and numeric interpolation. Reduced motion also disables those animations. Explicit scene controls remain usable in a still frame. Offscreen scenes and hidden documents stop their ambient frame clock; the quiet mode disposes the canvas. Device data-saving and forced-colors preferences start with the labeled simplified view. A WebGL/context failure offers a scene retry and leaves all source-linked records available.

Numeric animation interpolates between actual responses only. The first value is shown exactly at mount; the accessible value changes immediately to the received value while its visual copy transitions. Material transaction stages still change only after the existing confirmed command and read-back. There is no new REST route, WebSocket contract, authority, financial inference or persistence path.

The passport metric strip uses native swipe/snap scrolling below 768px and explicit previous/next buttons as a non-drag alternative. Desktop/tablet metrics retain the responsive grid. The existing document scroll owner, session-clearing rules, drawer focus behavior, pending/failure recovery and AR/EN contracts remain in effect.
