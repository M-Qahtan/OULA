# OULA — five worlds, one connected workspace

Candidate branch: `codex/rebuild-cinematic-worlds-20261019`. Review and current CI: [PR #122](https://github.com/M-Qahtan/OULA/pull/122).

The gateway now takes a visitor from a world, section and branch into an existing authenticated workspace task, retains that choice through sign-in or cancellation, and provides a return link. Original gateway art and gold/world marks are preserved. Desktop uses the five portal composition; mobile uses five circular marks in a 2+2+1 arrangement. All actions, navigation, forms and search results are native semantic UI.

| Family | Sections | Branches | Entries into existing workspace forms | Planned branches |
|---|---:|---:|---:|---:|
| PULSE / نبض | 6 | 18 | 12 | 6 |
| HABITAT / موئل | 6 | 18 | 12 | 6 |
| NEXUS / وَصل | 6 | 18 | 6 | 12 |
| FORGE / هندسة | 6 | 18 | 0 | 18 |
| CIVIC / مدن | 6 | 18 | 0 | 18 |
| Total | 30 | 90 | 30 | 60 |

Entries can share a service/form. This count does not mean 30 distinct operational services. Planned branches expose their scope and prerequisites without an execution action. Search ranks Arabic/English task labels locally and normalizes Arabic diacritics. It does not call an AI provider or record audio.

## HABITAT care

The shared cookie-authenticated BFF consumes the existing Java management enrollment/read, obligation creation, Guardian assessment and action completion endpoints. Each write requires the appropriate session purpose/scope and a human confirmation, keeps an identical retry's idempotency key, and reads the saved state back. Inputs carry `USER_DECLARED`. Resolution is a human declaration, not an inspection certificate.

The browser integration test creates an isolated fictional property, enters care from the HABITAT branch, enrolls it, saves an overdue obligation, replays the same command without duplication, assesses it, records resolution, reloads, and checks the persisted `SATISFIED` / `RESOLVED` / `COMPLETED` states. A missing completion scope found in the development fixture is fixed only for the fictional local property manager. Production IAM grants remain server-owned.

The whole OpenAPI document is parseable with unique keys and resolved internal references. All 96 existing paths are retained. Six management response schemas describe the existing Java records, including nullable legacy values. BFF validation rejects mismatched property/workspace data in nested resources. No database migration or parallel service is added.

## Local verification and reproducibility

- TypeScript and the production Next.js build pass.
- 131 unit tests pass, including full OpenAPI validation, catalog/link/redirect validation, authorization boundaries and nullable response compatibility.
- 52 Playwright tests pass without retries, skips or flaky results against the explicitly synthetic local OIDC/API harness. They cover all 90 branches, Arabic/English, keyboard/focus, accessibility checks, account/session isolation and context return. These are not Java/PostgreSQL results.
- Seven production-build public UI captures have zero horizontal overflow and zero uncaught page errors. The script is `apps/web/scripts/capture-worlds.mjs`; machine checks are in `WORLDS_VISUAL_CHECKS_20261019.json`.
- Fresh canonical Java/PostgreSQL and all required CI results are recorded on PR #122. Release acceptance must use the latest PR head, not earlier green commits.

Run the application using [CONNECTED_WORKSPACE_RUNBOOK.md](CONNECTED_WORKSPACE_RUNBOOK.md). Capture public UI from the built frontend with `node apps/web/scripts/capture-worlds.mjs`; optionally set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` for an already installed Chromium. Browser dependencies must be installed first.

## Visual review

| Screen | Capture |
|---|---|
| Desktop gateway | [Gateway](screenshots/oula-gateway-desktop-20261019.jpg) |
| Mobile gateway | [Mobile](screenshots/oula-gateway-mobile-20261019.jpg) |
| PULSE passport sources | [PULSE](screenshots/oula-pulse-passport-20261019.jpg) |
| HABITAT add an obligation | [HABITAT](screenshots/oula-habitat-care-20261019.jpg) |
| NEXUS deal entry | [NEXUS](screenshots/oula-nexus-deals-20261019.jpg) |
| FORGE planned scientific scope | [FORGE](screenshots/oula-forge-scope-20261019.jpg) |
| CIVIC planned indicators | [CIVIC](screenshots/oula-civic-scope-20261019.jpg) |

Captures show the real production-built public frontend without an authenticated identity or simulated private records. The original raster lettering remains bilingual; native controls and task content switch language. Pixel-identical certification is not claimed.

## Review and release boundary

Role selection changes presentation, never permission. Tokens remain server-side in the encrypted HttpOnly session; the finite login return path discards external URLs, arbitrary identifiers, authority and credentials. Identity/authority changes clear private gateway input and the existing workspace's private state.

Production IdP onboarding, live bank/government/payment/signing integrations, engineering engines and city engines are outside this candidate. Independent backend/frontend contract review and maintainer merge remain required. Revert the delivery commits to roll back; there is no schema migration to reverse.
