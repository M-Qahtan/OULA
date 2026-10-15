# OULA Local Executive Demonstration

**Status:** Development-only. A local demo does not prove production integrations or licensing.

## Prerequisites

- Java 21, Maven, Node 22, npm, Docker with Compose and Bash (macOS/Linux/WSL).
- Clone the real repository and run from its root:

```bash
bash scripts/start-local-expo.sh
```

The script starts loopback-only PostGIS, packages the existing Java backend, checks its health, installs/builds the Next.js frontend and serves it on loopback. It uses the **dev-auth** profile with fixed fictional users; no bank, municipal, title-registration or financial transaction occurs.

## Presentation journey

1. Open `http://127.0.0.1:3000/gateway` for the five OULA worlds and responsive navigation.
2. Open `http://127.0.0.1:3000/` for the browser → signed development JWT → Java/PostgreSQL buyer Golden Path.
3. Once PR #83 Guardian flow has passed CI and merged, open `http://127.0.0.1:3000/guardian-proof` for a separate fictional property-manager workspace.

Nothing from this setup is a real estate listing or a legal record. Use `DEV_TEST_ONLY` labels during the presentation.

## Shutdown and data

Press Ctrl+C to stop app processes. To stop PostGIS:
```bash
docker compose -f compose.local.yml down
```
To erase the fictional local dataset and start clean:
```bash
docker compose -f compose.local.yml down -v
```
**Warning:** `down -v` destroys the local development volume. Never use these commands on production infrastructure. This stack binds DB and web/API only to 127.0.0.1. Use a private, trusted computer.

## Quality gates before field review

- `mvn -B -ntp verify`
- `cd apps/web && npm run typecheck && npm test && npm run build`
- Browser integration workflow with real PostgreSQL/PostGIS
- Exact-head CodeQL, negative authorization, smartphone accessibility and bilingual visual QA

This launcher is a convenience script; it must be tried on the intended machine before any stakeholder event.
