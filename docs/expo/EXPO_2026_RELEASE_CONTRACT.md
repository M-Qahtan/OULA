# OULA EXPO-2026 — Release Contract v1.0
**Status:** RELEASE-CANDIDATE SPEC / proposed for review; not a declaration of production readiness.
**Owner:** OULA executive coordination. **Decision maker:** founder.
**Deadline:** usable exhibition pilot by 2026-10-19 (7 Saudi business days from 2026-10-11); test/acceptance 20–22; rehearsal 23; freeze 24.
**Canonical source:** Java backend in `M-Qahtan/OULA`, `main`; OpenAPI `contracts/openapi.yaml` (verified v1.10.0 on 10 Oct).
**Frontend source:** existing Lovable Oula LifeFlow project `c62b2bd4-cfa8-450c-b08f-517cc9430bc7`; not a parallel source of truth.
**Executive tracking:** GitHub #38 and children #39–#44.
**No ROS integration in this release.**

## Promise we are allowed to make
A controlled, demonstrable, Arabic-first, explainable property decision and journey prototype, with a canonical Java service layer and provenance-aware mock vs live boundaries.

## Claims not allowed
No claim of true government registry lookup, Ejar submission, bank reconciliation, payment custody, licensed brokerage, live IoT sensors, deployed municipal digital twins, audited investment return, or real-world causal proof unless demonstrated and authorized separately. Mark `DEMO`, `SIMULATED`, or `SANDBOX` clearly; all fictional identities/properties are marked DEMO even if fields are internally consistent.

## Experience scope / 3-minute Golden Journey
| UI stage | Data owner | Available canonical endpoint | Release truth |
|---|---|---|---|
| 1 Understand Me / LifeGraph | people/intent | no proven public create-intent API yet | Demo until #39 adds reviewed endpoint |
| 2 Candidate Retrieval + LifeFit | matching | `POST /v1/intents/{intentId}/matches` | Server for seeded authorized intent; demo fallback explicit |
| 3 Property Passport | property | `GET /v1/properties/{propertyId}/passport` | Must expose provenance; existing OpenAPI response schema incomplete (#39/#41) |
| 4 Living Twin | property/vitals/operations | property-vitals APIs available, no real IoT | SIMULATED only |
| 5 Asset Performance | operations/vitals | `GET /v1/properties/{propertyId}/vitals/latest` | Only evidence-backed domain values; incomplete coverage = UNKNOWN |
| 6 Buy/Rent Decision Simulator | decision/intelligence | no proven canonical frontend-ready simulation contract | DEMO assumptions/range only; no financial advice |
| 7 Deal Room | transaction | `GET /v1/transactions/{transactionId}`, transitions API | Backend state machine only if authenticated and schema-aligned; no real contract/payments |
| 8 Built World Intelligence | future research | none production-ready | Vision/explanatory DEMO, never claim simulated engineering is certified |

## Joint frontend/backend rules
1. Server owns person/property/transaction state and permissions. Frontend owns navigation, presentation, input validation, accessible feedback.
2. Typed API adapter, explicit demo adapter; a demo screen may never silently switch its source to fake "LIVE".
3. JWT OAuth scopes and workspace claim authorize, `X-OULA-Purpose` contextualizes. Do not store bearer tokens in localStorage or commit secrets.
4. Every backend call must have schema-aligned success/error handling, pending/empty/denied/timeout states, and an explicit source label.
5. `VERIFIED ≠ DECLARED ≠ OBSERVED ≠ ESTIMATED ≠ AI_INFERRED`. For test records `DEMO-VERIFIED` means only within fictional sandbox and never real government verification.
6. Mapping demo Deal Room stages to canonical backend transaction states must be explicit, reviewed and tested. No invented transaction transition.
7. Preserve modular monolith boundary tests and DB migrations; no independent Supabase second source of domain truth.
8. Browser experience 1920×1080/1024×768/390×844 Arabic RTL and English LTR; keyboard only and reduced motion work.

## Design Direction — Intelligent Prestige
Human + Place + Intelligence + Elevation: contemporary editorial typography, measured spacing, spatial-field motif, warm human materiality, quiet deep-ink contrasted with pearl and controlled intelligent accent. Icon/symbol must not be redesigned casually: approved future transformational continuous-ring concept; use text wordmark if vector missing. No roofs/pins/skyline/sparkles. The eight-step journey needs one elegant visual language, clear information hierarchy and meaningful micro-interactions (not gratuitous effects).
First 10 seconds: emotionally compelling but specific system thesis; first 3 minutes: a complete, repeatable cause/effect demo with proof and authorization indicators.

## Required delivery owners / acceptance
| Task | Deliverable | Gate |
|---|---|---|
| #39 BE | new critical routes only when needed + official schemas + authentic seed fixtures | Maven verify / real PostGIS / CI green |
| #40 UX | coherent premium UI rework in EXISTING Lovable | strict typecheck + tests + production build AFTER final edit + full browser QA |
| #41 INT | auth/CORS/adapter contract matrix, one authenticated live slice | workspace isolation, no undocumented endpoint |
| #42 QA | independent timed walk-through, browser and security regression | no open P0; evidence-linked GO/NO-GO |
| #43 SRE | approved secure staging, rollback, offline fallback | no paid launch without founder approval |
| #44 FIELD | event compliance, demonstration script, permissioned test data | real dry-run and founder signoff |

### Blockers as of 10 Oct
- Lovable quality pass completed (10/10 reported); **next premium redesign blocked by Lovable exhausted workspace credits**. Founder action needed via Lovable billing.
- Lovable has no authenticated backend user/session integration. A configured API address alone is insufficient.
- GitHub #37 Wave22 is green but a **draft unrelated to critical exhibition slice**; hold unless dependencies prove otherwise.
- #39–#44 are work assignments in issue tracking, **not proof that human/coding agents are actively executing them**.
- Environment budget, preview/public access and actual exhibition registration not yet approved.

## Hourly operating rhythm (Asia/Riyadh)
| Local hour | Checkpoint |
|---|---|
| 08:00 | risks / P0 / unblock decisions |
| 09:00 | contract owner/API matrices |
| 10:00 | scoped implementation |
| 11:00 | pair integration |
| 12:00 | run unit checks |
| 13:00 | real PostGIS/API CI |
| 14:00 | browser integration / UX |
| 15:00 | QA and accessibility |
| 16:00 | defect correction and re-test |
| 17:00 | timed three-minute showcase rehearsal |
| 18:00 | record evidence, blockers and next task |
| 19:00 | founder daily executive summary |

No claim of 24/7 human or coding agent work. Hourly automation only **monitors** and escalates; real implementation depends on active coding capacity and approved tools.

## Release GO/NO-GO
`GO` for field DEMO only if: final frontend TS/tests/build green; backend CI+CodeQL green; real secured E2E golden slice or unmistakably labeled offline DEMO fallback; no dead primary CTAs; stable repeatable reset; UX desktop/tablet/mobile RTL; verified presenter gear; no undocumented integration claims; owner signoff.
`NO-GO` for production or live real estate/financial interactions without legal/licensing, privacy, deployment security and independently sourced property evidence.
