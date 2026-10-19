# OULA spatial interiors — 19 October 2026

Review: [PR #123](https://github.com/M-Qahtan/OULA/pull/123). Candidate: `codex/spatial-worlds-20261019`, including the integrated workspace from #122. No production deployment is performed.

## Result

The five world interiors and connected workspace now share a cinematic spatial shell: original OULA/world marks, a Riyadh-inspired architectural environment, glass instruments, contextual luminous edges and metallic gold actions. The main gateway is preserved. Arabic uses bundled IBM Plex Sans Arabic; English uses DM Sans. Tailwind v4 theme/utilities are added without Preflight so the earlier proof routes retain their behavior.

| Surface | Implemented visual behavior | Data boundary |
|---|---|---|
| PULSE | Cyan spatial canvas, task paths and shared assistant | Existing intent, matching and passport contracts |
| HABITAT | Teal glass controls and asset-care entry | Existing declared intake, management, obligations and actions |
| NEXUS / Deal Room | Amber state track, version/count instruments and offer/viewing records | Existing transaction read/write contracts; human confirmation and read-back |
| Property Passport | Asset identity, exact coverage ring, source-linked fact cards and knowledge ledger | `PropertyPassport`, including unknown currency and server truth statuses |
| Decision Space | Amber canvas and matching/evidence/risk/decision instruments | Values only from recorded matching and recommendation responses |
| FORGE | Laser-blue shared shell and illustrative engineering context | Scientific engines remain planned; no invented results |
| CIVIC | Violet shared shell and illustrative urban context | City indicators/providers remain planned; no invented live metrics |

## Responsive system

| Width | Navigation | Assistant | Content |
|---|---|---|---|
| 1280px and above | Left sidebar | Persistent right panel | Center canvas and responsive metric grid |
| 768–1279px | Compact sidebar | Native modal sliding panel | Two-column metric grid where space permits |
| Below 768px | Floating bottom hub with safe-area space | Modal panel with internal scrolling | One-column instruments and forms |

The physical left/right shell remains consistent for RTL and LTR content. One assistant subtree survives resizing. Native modal background isolation, Tab wrapping, Escape and trigger-focus restoration are tested. Document scrolling owns long forms; the shared shell does not clip them to viewport height. Reduced motion disables nonessential animation.

## State and service alignment

`WorkspaceQueryBoundary` creates a private memory-only TanStack Query client inside the existing authority-keyed journey. Passport and deal reads can reuse a 20-second cache during navigation. Explicit refresh and post-command reads are fresh. Account/workspace/purpose/scope changes cancel reads and clear the cache; no private cache is persisted in browser storage.

Navigation, display layers, sliders and approval selections react immediately. Material commands show a pending skeleton and prevent duplicate activation; saved stages require the Java/BFF response and read-back. On failure, the old recorded stage remains and the same command key is reused for an identical retry. No financial or permission change is represented as saved before the server confirms it.

The implementation uses the existing typed REST/BFF endpoints. The current repository exposes no contracted WebSocket feed for these widgets, so none is invented. Java 21, Spring Boot 4.1.1, PostgreSQL/PostGIS, authorization, migrations and API lifecycle contracts are retained.

## Verification

- TypeScript and the production Next.js build pass.
- 131 unit tests pass.
- 59 Playwright browser tests pass without retries, skips or flaky results on the candidate UI, using the explicitly synthetic OIDC/API harness. This includes all 90 branches, Arabic/English, release viewports, accessibility, session isolation, cache reuse/fresh refresh, modal focus/resizing and pending/failed offer retry behavior.
- The scoped strict premium UI audit reports zero findings. The official `designmd lint DESIGN.md` reports zero errors/warnings.
- Eleven production-built captures have zero horizontal overflow and zero uncaught page errors; see `SPATIAL_VISUAL_CHECKS_20261019.json`.
- Fresh Java/PostgreSQL and GitHub workflow results for the final head are recorded on PR #123. Previous green commits do not substitute for the final-head checks.

The public world captures contain no private records. The passport, deal, decision and authenticated mobile captures use fixed fictional **synthetic contract-harness records**, not Java/PostgreSQL or real property data. Separate browser integration CI exercises the canonical Java/database path. The scenery is labeled illustrative and is not a live map, property photo or engineering result. Asset provenance and the generation prompt are in `apps/web/public/visuals/spatial/ASSET.md`.

Capture command from the repository root: `node apps/web/scripts/capture-spatial.mjs`. It starts its own loopback Next.js and synthetic test harness and shuts both down afterward. Install the locked frontend dependencies and Playwright Chromium first; an installed executable can be selected with `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

## Screens

| Screen | Preview |
|---|---|
| PULSE | [Desktop](screenshots/spatial/oula-pulse-desktop.jpg) |
| HABITAT | [Desktop](screenshots/spatial/oula-habitat-desktop.jpg) · [Mobile](screenshots/spatial/oula-habitat-mobile.jpg) |
| NEXUS | [Desktop](screenshots/spatial/oula-nexus-desktop.jpg) |
| FORGE | [Desktop](screenshots/spatial/oula-forge-desktop.jpg) |
| CIVIC | [Desktop](screenshots/spatial/oula-civic-desktop.jpg) |
| Property Passport | [Desktop](screenshots/spatial/oula-property-passport.jpg) · [Mobile](screenshots/spatial/oula-property-passport-mobile.jpg) |
| Deal Room | [Desktop](screenshots/spatial/oula-deal-room.jpg) |
| Decision Space | [Desktop](screenshots/spatial/oula-decision-space.jpg) |
| Assistant drawer | [Mobile](screenshots/spatial/oula-assistant-mobile.jpg) |

## Review and rollback

`DESIGN.md` documents intentional changes in typography, spatial depth, responsive layout and token ownership. `UX-CONTRACT.md` preserves the prior authority/outcome/security rules and adds the shell/cache contract. The separate family stylesheet is retired; shared instruments and shell styles live in `styles/spatial.css`.

Independent review and maintainer merge are required. Production IdP onboarding, external regulated providers and scientific/city engines remain outside this candidate. Revert the spatial UI commits to roll back; no database migration is involved.
