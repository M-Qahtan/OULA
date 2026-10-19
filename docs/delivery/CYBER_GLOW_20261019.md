# OULA interactive cyber-glow interiors

Candidate: [PR #123](https://github.com/M-Qahtan/OULA/pull/123), continuing the protected spatial workspace. This is a frontend-layer change; Java, database migrations, OpenAPI and the private BFF/session/cache boundaries remain unchanged.

## Design and implementation

All ten newly supplied images were inspected directly. Their continuous luminous edges, dark glass, original OULA rings, world-specific light and reflected floor informed the implementation. The supplied montage is preserved unchanged as `apps/web/public/visuals/reference/cyber-glow-board-20261019.jpg` (SHA-256 `171e412eacf2df0bd9b3b0843928c5f258b7d6c1710d5fd5ca9d2497da5389ce`) for design review. The original gateway remains intact.

Property Passport now places source-bound identity and animated coverage above a responsive metric strip and an interactive district. The same environment and glass/capsule primitives serve the five world interiors, Deal Room and Decision Space. Arabic RTL and English LTR remain native UI, not raster lettering.

| Layer | Runtime owner | Behavior |
|---|---|---|
| Global motion | `components/spatial-experience.tsx`, Framer Motion 14.4.0 | Shared preference/pause control; private forms are not remounted by animation |
| Spatial rendering | `spatial-environment.tsx`, `spatial-city-canvas.tsx` | Deferred client-only Three.js 0.186.1, Fiber 9.8.2 and Drei 10.7.9 |
| Shader artwork | `lib/spatial-shaders.ts` | Pointer-reactive GLSL nebula, luminous façades/particles and reflected grid floor |
| Geometry/budget | `lib/spatial-city-model.ts` | Deterministic illustrative geometry; instanced buildings; bounded particles/DPR/frame scheduling |
| Glass and light | `styles/spatial.css`, `spatial-environment.css` | Shared canonical tokens, translucent sheets, gradient edges, ambient orbs and amber capsule actions |
| Numbers | `AnimatedNumber`, `SpatialMetric` | Animate between received values; initial/exact accessible value stays source-bound |
| Mobile instruments | `SpatialMetricStrip` | Native snap/swipe, keyboard focus and visible previous/next controls, including RTL arrows |
| Data and authority | Existing `browser-api`, BFF, schemas and `WorkspaceQueryBoundary` | Existing authenticated reads, human-confirmed commands, same idempotency/version rules |

Existing Google Fonts imports were replaced with the already-installed bundled fonts after a capture exposed an external-font request failure. No third-party graphics asset, texture CDN, HDRI, geocoder or analytics endpoint is introduced.

## Accuracy and resource limits

The district is **illustrative procedural geometry**, not surveyed Al-Malqa data, BIM, a live map or a verified digital twin. The district label is from the passport; the renderer supplies no property boundary, height, valuation, occupancy or engineering result. Missing facts/currency remain explicit. Existing typed REST endpoints are used; there is no contracted WebSocket feed to claim or fabricate.

On a wide canvas the renderer caps particles at 5,600, DPR at 1.5 and ambient scheduling at 30 fps; narrow canvases use 2,200 / 1.25 / 24. These are configured limits, **not measured physical-device performance guarantees**. The browser capture observes nine draw calls. There is no postprocessing chain or repeated per-building draw call.

The GPU module loads near the viewport. A demand loop stops ambient work offscreen or in a hidden document. Reduced motion and the global pause stop shader time, CSS ambience and numeric interpolation. Quiet view disposes the canvas. Context loss/unavailable WebGL shows a labeled fallback and scene retry while records/actions remain available. Data-saving/forced-colors preferences start in quiet view. Phone canvases preserve normal page scrolling; localized rotate/zoom/reset buttons provide the non-drag interaction.

The document retains scroll ownership for long forms. Desktop has physical left navigation / center canvas / right assistant. Tablet uses the existing modal assistant drawer. Mobile uses the illuminated floating bottom capsule. The authority-keyed query cache and session clearing behavior are retained.

## Verification

Fresh local verification: **133 unit tests and 67 browser tests pass**, with zero retries, skips or flaky results. TypeScript, a clean production build, the scoped strict UI audit and the official DESIGN.md lint pass; both audits have zero errors/warnings. Eleven captures have zero horizontal overflow and zero runtime/asset-loading errors. The 1280 × 608 MP4 is independently verified with FFprobe. Details are recorded in `CYBER_VERIFICATION_20261019.json`; final-head GitHub checks are recorded on PR #123. The local commands are:

```sh
cd apps/web
npm ci
npm run typecheck
npm test
npm run build
OULA_TEST_SOFTWARE_WEBGL=1 npm run test:e2e -- --retries=0
```

Browser checks include the existing 90-branch/session/command regression suite and new GPU controls, bounded draw calls, motion pause/offscreen/reduced behavior, context-loss retry, unavailable WebGL, exact accessible price interpolation, mobile swipe alternatives and AR/EN accessibility. The hover-contrast regression explicitly exercises the legacy/new-style boundary. No test or source contract was removed to make the visual change pass.

The synthetic OIDC/API harness used by local browser tests and authenticated captures is **not Java/PostgreSQL or real property data**. The separate canonical browser-integration CI must pass on the final head before integration readiness is asserted. Software WebGL is enabled only for trusted loopback test content on headless CI or explicit local test runs.

Static UI evidence: `CYBER_UI_AUDIT_20261019.json`. Production capture evidence: `CYBER_VISUAL_CHECKS_20261019.json`. The official `designmd lint DESIGN.md` validates the documented token mapping; it does not substitute for runtime checks.

## Visual evidence

| Surface | Screenshot |
|---|---|
| PULSE | [Desktop](screenshots/cyber/oula-pulse-desktop.jpg) |
| HABITAT | [Desktop](screenshots/cyber/oula-habitat-desktop.jpg) · [Mobile](screenshots/cyber/oula-habitat-mobile.jpg) |
| NEXUS | [Desktop](screenshots/cyber/oula-nexus-desktop.jpg) |
| FORGE | [Desktop](screenshots/cyber/oula-forge-desktop.jpg) |
| CIVIC | [Desktop](screenshots/cyber/oula-civic-desktop.jpg) |
| Property Passport | [Desktop](screenshots/cyber/oula-property-passport.jpg) · [Mobile](screenshots/cyber/oula-property-passport-mobile.jpg) |
| Deal Room | [Desktop](screenshots/cyber/oula-deal-room.jpg) |
| Decision Space | [Desktop](screenshots/cyber/oula-decision-space.jpg) |
| Assistant | [Mobile](screenshots/cyber/oula-assistant-mobile.jpg) |

[Actual interactive scene recording](screenshots/cyber/oula-property-passport-interactive.mp4); provenance is in `CYBER_VIDEO_CAPTURE_20261019.json`.

Capture after a production build: `node scripts/capture-cyber.mjs`; `--smoke` captures only the passport and `--video` records pointer rotation/zoom/reset using Playwright video and FFmpeg. A custom installed Chromium may be supplied with `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Public world captures contain no private records; authenticated captures use the explicit fictional contract fixture. The video is an actual browser recording, not a generated concept video.

## Review and rollback

Independent review, contract compatibility and maintainer merge remain the release gates. No production deployment, provider activation or scientific/city-engine completion is claimed. The source and exact shader formulas are reviewable. Physical Safari/Android/iOS hardware profiling remains unverified; narrow/touch-layout browser checks are not a substitute for that device matrix.

Rollback is a revert of the cyber-glow commit. No database migration is involved. `DESIGN.md` records the approved change from opaque/metallic surfaces to luminous glass/capsules and the ownership of the live renderer; `UX-CONTRACT.md` retains the existing authority and failure invariants.
