---
version: alpha
name: OULA Spatial Workspaces
colors:
  night: "#030912"
  panel: "rgba(5, 16, 30, 0.72)"
  raised: "rgba(12, 28, 46, 0.76)"
  border: "rgba(174, 204, 233, 0.17)"
  text: "#edf5ff"
  muted: "#a8bdd3"
  primary: "#e9c88c"
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
  control: "12px"
  panel: "22px"
spacing:
  page: "24px"
  section: "24px"
components:
  button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.night}"
    rounded: "{rounded.control}"
    typography: "{typography.body}"
  panel:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.text}"
    rounded: "{rounded.panel}"
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
The founder's five supplied reference images define the new main-workspace identity: a navy night view over Riyadh, a restrained metallic ring, illuminated blue/teal/cyan/violet world accents, warm gold primary actions and calm glass panels. This is a role-shaped product workspace for Saudi-first use on desktop and mobile. It is not a static screenshot disguised as an application.

The recurring signature is the original gold OULA ring beside a contextual routing assistant. Supplied raster references remain explicitly illustrative scene/thumbnail material; navigation, forms, source labels, session, results and actions are real semantic components. Pixel-identical rendering cannot be certified from raster reference images alone.

Token ownership follows Model B: runtime CSS is canonical; this document mirrors and explains accepted values. `styles/spatial.css` owns the spatial palette, responsive geometry and aliases consumed by the retained form styles in `app/workspace.css`; `lib/spatial-theme.ts` maps active worlds/views to static Tailwind recipes; `components/workspace-primitives.tsx` owns icons, mark, status and product language. The root opens the five-family `/gateway`; the connected workspace is `/workspace`. The earlier light demo is retained at `/journey`, with its existing tokens in `app/globals.css`. Do not recolor that sibling route through workspace selectors.

## Colors
Night, panel and raised tones establish depth. Text and muted tokens provide readable type. Gold identifies the principal next step. World colors distinguish experiences and never confer authority. Red/success feedback always includes text, not color alone. Focus uses the active world accent. The only supported main-workspace theme is dark; a light-theme button is deliberately absent.

## Typography
IBM Plex Sans Arabic supports Arabic RTL, with bundled 400/500/600/700 weights; DM Sans supports English LTR and utility identifiers. Both are locally bundled to keep fonts available offline. Body line-height is 1.7; dense labels remain 10–13px and primary copy 13–15px. Headings range from 27px on mobile to 39px on wide desktops. Long server identifiers wrap, while mixed-script references carry explicit LTR direction.

## Layout
At 1280px and above, the shared spatial shell uses physical left navigation (184px), flexible center canvas and right assistant (292px), independent of content direction. At 1700px these become 210px / flexible / 330px. At 768–1279px the assistant is a modal drawer and the navigation remains a compact sidebar. Below 768px navigation becomes a floating bottom hub with safe-area padding. One assistant subtree persists through resizing and retains its input. The document owns content scrolling; forms remain natural height and the modal alone scrolls internally. Role selection changes presentation, not authority. PULSE, HABITAT, NEXUS, FORGE and CIVIC share this shell. PRISM remains the internal decision lens; deal and decision views use amber.

## Elevation & Depth
One scene carries visual emphasis. Translucent navy panels use 20–28px backdrop blur, fine luminous borders, an inset edge highlight and restrained world-colored glow. Gold actions receive a metallic gradient. The central illustrative Riyadh environment carries the visual emphasis; evidence and form surfaces stay quiet and legible. Decorative scenes have explicit non-live labels. Form errors are persistent inline panels. No fabricated map, financial or scientific measure is promoted to operational truth.

## Shapes
Controls use 12px radii; panels use 22px, reduced to 16–18px on mobile. The gateway and family headers crop the original supplied gold ring and five portal marks from the preserved raster asset; the spatial shell shares these original marks while existing operational subcomponents retain the code-rendered mark. No replacement vector or pixel-identical certification is claimed. Icons share 1.5px outline strokes with visible action labels.

## Components
Shared controls have hover, visible focus, active, disabled and stable busy states. Native select popups are intentionally platform-owned; CSS does not remove native affordances. Forms use app-owned validation with `noValidate`. `StatusBanner` supplies persistent accessible success/error/information feedback. The contextual assistant classifies locally and suggests navigation; it does not listen through a microphone or execute business commands.

Framer Motion supplies a 350ms entry reveal and a restrained 12s scene settle; no keyed page animation remounts private forms. CSS handles control transitions and pending skeleton shimmer. Navigation and display controls respond immediately; material records only advance after server confirmation. TanStack Query owns memory-only private reads, canceled and cleared on authority changes. Reduced motion stops animation and transitions. Engineering/city sliders and checkboxes change presentation only, with an explicit concept label. Scientific calculation is not implied by visual filters.

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
