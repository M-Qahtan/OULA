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
