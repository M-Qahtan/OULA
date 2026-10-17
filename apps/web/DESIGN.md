---
version: alpha
name: OULA Main Workspaces
colors:
  night: "#030a13"
  panel: "#091522"
  raised: "#102033"
  border: "#263b52"
  text: "#edf4fc"
  muted: "#a7b8cb"
  primary: "#e4bf82"
  pulse: "#61bbff"
  habitat: "#40d8c3"
  forge: "#40d6f7"
  civic: "#be8aff"
  danger: "#ffaaaa"
  success: "#70dcb0"
typography:
  body:
    fontFamily: "Tajawal, system-ui, sans-serif"
  utility:
    fontFamily: "DM Sans, system-ui, sans-serif"
rounded:
  control: "12px"
  panel: "22px"
spacing:
  page: "28px"
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

The recurring signature is the OULA ring beside a contextual routing assistant. Supplied raster references remain explicitly illustrative scene/thumbnail material; navigation, forms, source labels, session, results and actions are real semantic components. Pixel-identical rendering cannot be certified from raster reference images alone.

Token ownership follows Model B: runtime CSS is canonical; this document mirrors and explains accepted values. `app/workspace.css` owns shared dark tokens and geometry; `lib/workspaces.ts` maps world accents; `components/workspace-primitives.tsx` owns icons, mark, status and product language. The root opens the five-family `/gateway`; the connected workspace is `/workspace`. The earlier light demo is retained at `/journey`, with its existing tokens in `app/globals.css`. Do not recolor that sibling route through workspace selectors.

## Colors
Night, panel and raised tones establish depth. Text and muted tokens provide readable type. Gold identifies the principal next step. World colors distinguish experiences and never confer authority. Red/success feedback always includes text, not color alone. Focus uses the active world accent. The only supported main-workspace theme is dark; a light-theme button is deliberately absent.

## Typography
Tajawal supports Arabic RTL, with bundled 400/500/700/800 weights; DM Sans supports English LTR and utility identifiers. Both are locally bundled to keep fonts available offline. Body line-height is 1.65; dense labels remain 10–13px and primary copy 13–15px. Headings range from 27px on mobile to 39px on wide desktops. Long server identifiers wrap, while mixed-script references carry explicit LTR direction.

## Layout
Wide desktop uses a 112px navigation rail, flexible main canvas and 308px contextual assistant. At 960px, the assistant follows the main canvas. At 640px, navigation becomes a horizontally scrollable document-flow strip and forms use one column. Document scrolling owns the page; no fixed-height shell clips forms or results. Role selection changes the view preference, not security claims. The accepted top-level families are PULSE, HABITAT, NEXUS, FORGE and CIVIC; PRISM is an internal legacy decision lens. Safe actions remain visible at 390px.

## Elevation & Depth
One scene carries visual emphasis. Quiet panels use tonal differences and 1px borders; gold actions receive minimal glow. Decorative scenes have explicit non-live labels. Form errors are persistent inline panels. No fabricated map, financial or scientific measure is promoted to operational truth.

## Shapes
Controls use 12px radii; panels use 22px, reduced to 16–18px on mobile. The ring is a code-rendered UI mark inspired by the supplied direction, not an assertion of a source-vector reproduction. Icons share 1.5px outline strokes with visible action labels.

## Components
Shared controls have hover, visible focus, active, disabled and stable busy states. Native select popups are intentionally platform-owned; CSS does not remove native affordances. Forms use app-owned validation with `noValidate`. `StatusBanner` supplies persistent accessible success/error/information feedback. The contextual assistant classifies locally and suggests navigation; it does not listen through a microphone or execute business commands.

Motion is limited to a low-amplitude waveform following a local assistant suggestion and 160ms control transitions. Reduced motion stops animation and transitions. Engineering/city sliders and checkboxes change presentation only, with an explicit concept label. Scientific calculation is not implied by visual filters.

## Do's and Don'ts
- Keep the supplied world identity, bilingual language, native semantics and server truth together.
- Render unknown price currency, absent facts and limited evidence explicitly.
- Never show a demo result after a failed workspace request.
- Never treat role selection, a ring animation or a recommendation as permission or execution.
