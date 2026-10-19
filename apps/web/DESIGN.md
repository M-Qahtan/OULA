---
version: alpha
name: OULA Cyber-Glow Spatial Workspaces
colors:
  night: "#030912"
  panel: "rgba(10, 16, 28, 0.4)"
  raised: "rgba(13, 32, 48, 0.58)"
  border: "rgba(174, 204, 233, 0.23)"
  text: "#edf5ff"
  muted: "#a8bdd3"
  primary: "#e9c88c"
  action: "#fbbf24"
  pulse: "#22d3ee"
  habitat: "#2dd4bf"
  forge: "#60a5fa"
  civic: "#c084fc"
  danger: "#ffaaaa"
  success: "#70dcb0"
typography:
  body:
    fontFamily: "IBM Plex Sans Arabic, system-ui, sans-serif"
  utility:
    fontFamily: "DM Sans, system-ui, sans-serif"
rounded:
  control: "16px"
  panel: "28px"
  capsule: "999px"
spacing:
  page: "24px"
  section: "24px"
components:
  button:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.action}"
    rounded: "{rounded.capsule}"
    typography: "{typography.body}"
  panel:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.text}"
    rounded: "{rounded.panel}"
  workspace:
    backgroundColor: "{colors.night}"
    textColor: "{colors.text}"
  raised-panel:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.muted}"
  divider:
    backgroundColor: "{colors.border}"
  pulse-label:
    textColor: "{colors.pulse}"
  habitat-label:
    textColor: "{colors.habitat}"
  forge-label:
    textColor: "{colors.forge}"
  civic-label:
    textColor: "{colors.civic}"
  error-message:
    textColor: "{colors.danger}"
  success-message:
    textColor: "{colors.success}"

---
# OULA Main Workspaces

## Overview
The founder's ten supplied reference images define the main-workspace identity: a navy Riyadh night, the original metallic ring, continuous luminous cyan/teal/blue/violet edges, amber capsule actions and floating glass instruments. The October 19 cyber-glow brief explicitly strengthens depth, reflected light and interactive 3D. This is a role-shaped product workspace for Saudi-first use on desktop and mobile. It is not a static screenshot disguised as an application.

The recurring signature is the original gold OULA ring beside a contextual routing assistant. Supplied raster references remain explicitly illustrative scene/thumbnail material; navigation, forms, source labels, session, results and actions are real semantic components. Pixel-identical rendering cannot be certified from raster reference images alone.

Token ownership follows Model B: runtime CSS is canonical; this document mirrors and explains accepted values. `styles/spatial.css` owns the spatial palette, responsive geometry and aliases consumed by the retained form styles in `app/workspace.css`; `lib/spatial-theme.ts` maps active worlds/views to static Tailwind recipes; `components/workspace-primitives.tsx` owns icons, mark, status and product language. The root opens the five-family `/gateway`; the connected workspace is `/workspace`. The earlier light demo is retained at `/journey`, with its existing tokens in `app/globals.css`. Do not recolor that sibling route through workspace selectors.

## Colors
Amber capsules use `--os-action: #fbbf24` and the gradient endpoint `--os-action-end: #fb923c`; the endpoint remains a CSS/prose token because the component-token schema has no gradient-stop field.
Night, panel and raised tones establish depth. Text and muted tokens provide readable type. Metallic gold retains the brand identity. Amber-to-orange translucent capsules identify the principal next step. World colors distinguish experiences and never confer authority. Red/success feedback always includes text, not color alone. Focus uses the active world accent. The only supported main-workspace theme is dark; a light-theme button is deliberately absent.

## Typography
IBM Plex Sans Arabic supports Arabic RTL, with bundled 400/500/600/700 weights; DM Sans supports English LTR and utility identifiers. Both are locally bundled to keep fonts available offline. Body line-height is 1.7; dense labels remain 10–13px and primary copy 13–15px. Headings range from 27px on mobile to 39px on wide desktops. Long server identifiers wrap, while mixed-script references carry explicit LTR direction.

## Layout
At 1280px and above, the shared spatial shell uses physical left navigation (184px), flexible center canvas and right assistant (292px), independent of content direction. At 1700px these become 210px / flexible / 330px. At 768–1279px the assistant is a modal drawer and the navigation remains a compact sidebar. Below 768px navigation becomes a floating bottom hub with safe-area padding. One assistant subtree persists through resizing and retains its input. The document owns content scrolling; forms remain natural height and the modal alone scrolls internally. Role selection changes presentation, not authority. PULSE, HABITAT, NEXUS, FORGE and CIVIC share this shell. PRISM remains the internal decision lens; deal and decision views use amber.

## Elevation & Depth
One GPU-rendered district carries visual emphasis. Floating translucent sheets use 32px backdrop blur, a gradient edge, an inset highlight and a deep 25px / 50px shadow. World-colored light orbs and a floor reflection establish depth. Actions use transparent amber/orange gradients, bright amber text and a 15px glow growing to 25px on hover. Evidence and form surfaces stay legible. Decorative scenes have explicit non-live labels. Form errors are persistent inline panels. No fabricated map, financial or scientific measure is promoted to operational truth.

## Shapes
Inputs use 16px radii; panels use 28px, reduced to 20px on mobile. Primary actions, scene controls and the mobile navigation use 999px capsule radii. The gateway and family headers crop the original supplied gold ring and five portal marks from the preserved raster asset; the spatial shell shares these original marks while existing operational subcomponents retain the code-rendered mark. No replacement vector or pixel-identical certification is claimed. Icons share 1.5px outline strokes with visible action labels.

## Components
Shared controls have hover, visible focus, active, disabled and stable busy states. Native select popups are intentionally platform-owned; CSS does not remove native affordances. Forms use app-owned validation with `noValidate`. `StatusBanner` supplies persistent accessible success/error/information feedback. The contextual assistant classifies locally and suggests navigation; it does not listen through a microphone or execute business commands.

Framer Motion supplies shared entry transitions, layout-position transitions and 650ms interpolation between received numeric values; no keyed page animation remounts private forms. A number starts at its first actual value, not a fabricated zero. Its accessible copy always reports the exact current record value. CSS handles 500ms control transitions and pending skeleton shimmer. Navigation and display controls respond immediately; material records only advance after server confirmation. TanStack Query owns memory-only private reads, canceled and cleared on authority changes. Reduced motion stops ambient animation and counters. A global pause control also stops CSS ambience and the shader clock; explicit 3D controls remain available. Engineering/city sliders and checkboxes change presentation only, with an explicit concept label. Scientific calculation is not implied by visual filters.

## Do's and Don'ts
- Keep the supplied world identity, bilingual language, native semantics and server truth together.
- Render unknown price currency, absent facts and limited evidence explicitly.
- Never show a demo result after a failed workspace request.
- Never treat role selection, a ring animation or a recommendation as permission or execution.

## Five-world discovery and connected entry

`/gateway` preserves `public/visuals/reference/gateway-original.png` unchanged. Wide screens place semantic, keyboard-accessible links over the five original portals; at 900px and below, the composition changes to five circular marks in a 2+2+1 layout. Native buttons and text remain interactive. The embedded illustration lettering does not change when switching languages; native controls and task content do.

Each family has six sections and three branches per section: 90 navigable branches. Thirty entries route into existing workspace forms (12 PULSE, 12 HABITAT, six NEXUS); these are entry points, not 30 separate services. The remaining 60 branches describe planned capabilities with prerequisites and no operational launch action. FORGE and CIVIC do not claim working simulation engines.

`lib/family-catalog.ts` owns the bilingual task catalog, parent/child validation, local ranked search and finite task destinations. Role selection is a view preference. Account authorization remains server-owned. A same-origin, allowlisted return path retains world/section/branch/role/language through sign-in and cancellation; arbitrary URLs, credentials, workspace IDs and scopes are discarded. The connected workspace shows the source task and return link and focuses its relevant field after authentication.

Gateway search uses native dialog semantics, visible focus, Escape and focus restoration. It performs local discovery; it neither sends prompts to an AI provider nor records audio. Account, workspace or authority changes clear private gateway input and close its dialog. Motion respects reduced-motion preferences. Production-build captures and overflow/page-error checks are in `docs/delivery/screenshots` and `WORLDS_VISUAL_CHECKS_20261019.json`.

## Spatial transformation — 19 October 2026

North Star: the original OULA gold ring and immersive Saudi city scene, surrounded by functional glass instruments. All five world interiors, Property Passport, Deal Room and Decision Space share `SpatialShell`, `SpatialScene`, `GlassPanel`, `SpatialMetric` and `SpatialSkeleton`. The original main gateway is preserved. Tailwind v4 theme/utilities are loaded without Preflight to preserve legacy proof-route semantics. Native selects/date pickers remain platform-owned; the assistant uses the native dialog with endpoint focus wrapping and explicit close/Escape restoration.

| Token / behavior | Canonical runtime owner | Consumers |
|---|---|---|
| Night/text/palette | `styles/spatial.css` `--os-*` | Shell, panels, legacy `--night-*` aliases |
| World identity / amber decision | `lib/spatial-theme.ts` + `data-tone` CSS | Contextual glow, focus, borders |
| Arabic/English typography | Bundled IBM Plex Sans Arabic + DM Sans in `spatial.css` | Shared shell and forms |
| Responsive geometry | `SpatialShell` + `spatial.css` breakpoints | Both `/gateway/[world]` and `/workspace` |
| Metrics | Typed Java/BFF records | Passport facts/coverage, deal counts/stage/version, decision evidence/risk |
| Reads/cache | `WorkspaceQueryBoundary` inside the existing authority key | Passport and deal room; no persistent private storage |

The new generated environment is decorative and labeled as such. It contains no baked-in controls, numbers or live indicators. It is not a property photo or a geospatial dataset. See `public/visuals/spatial/ASSET.md` for provenance.

Intentional design drift resolved: Tajawal → IBM Plex Sans Arabic for spatial interiors; opaque family cards → shared glass shell and scenic canvas; stacked tablet assistant → modal drawer; mobile horizontal rail → floating bottom hub. Existing BFF, human confirmations, idempotency and exact server states remain the behavior owners. The older `/journey` presentation keeps its separate style.


## Cyber-glow rendering extension — 19 October 2026

The ten uploaded references were inspected directly: `1000966004.jpg`, `1000965033.png`, `1000965034.png`, `1000965031.png`, `1000965027.png`, `1000965030.png`, `1000965028.png`, `1000965025.png`, `1000965029.png`, and `1000965026.png`. The montage is retained under `public/visuals/reference/cyber-glow-board-20261019.jpg` for design review only. No raster labels, personal names, fabricated financial/city indicators or verification claims are used as functional widgets.

`SpatialEnvironment` is the shared visual owner; `spatial-city-canvas.tsx` dynamically imports Three.js/Fiber/Drei on the client near the viewport. `lib/spatial-shaders.ts` owns GLSL nebula, façade, point-glow and reflected-floor shaders. `spatial.css` owns the palette; resolved world/gold CSS colors become shader uniforms. `spatial-environment.css` owns rendering geometry, glass edges and ambient layers without a second palette.

The geometry is an explicitly illustrative, deterministic district. It is not an Al-Malqa survey, BIM model, live map or geospatial digital twin. The displayed district name and property facts still come from the passport response. No invented occupancy, valuation, risk or spatial measurements are emitted by the renderer.

One canvas uses instanced buildings and at most 5,600 points (2,200 on narrow canvases), capped DPR 1.5 / 1.25, a demand loop and a 30 / 24 fps ambient scheduling budget. These are resource limits, not measured device-performance promises. Offscreen and hidden-document ambient work stops. Quiet mode releases the canvas; WebGL failure/context loss leaves the independent data interface usable with a labeled fallback and a retry. Data-saver and forced-colors preferences begin in quiet mode. No HDRI, GLTF, CDN texture, geocoder or third-party graphics service is requested.

On mobile the passport metric strip uses native horizontal snap scrolling with visible previous/next controls. Other data matrices retain their established responsive grids. Every scene provides rotate, zoom and reset buttons; phone canvases preserve normal page scrolling rather than capturing touch gestures. Arabic/English labels and reduced-motion preferences govern these controls.

| Intentional evolution | Runtime owner | Preserved invariant |
|---|---|---|
| Opaque cards → luminous glass sheets | `spatial.css`, shared `GlassPanel` | Source labels and legible text |
| Metallic solid CTA → radiant amber capsule | Shared button selectors and action tokens | Labels, confirmation and disabled/busy semantics |
| Scenic bitmap → interactive illustrative district | `SpatialEnvironment`, deferred Fiber canvas | No GIS or backend-truth claim |
| Instant numeric update → bounded visual interpolation | `AnimatedNumber`, `SpatialMetric` | Exact accessible server value; no optimistic saved state |
| Remote font import → bundled existing fonts | `app/globals.css`, existing font packages | Legacy route typography; no font-network dependency |
