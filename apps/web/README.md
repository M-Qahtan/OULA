# OULA — Real Frontend (authoritative repository)

This is the **real OULA web frontend source** inside `M-Qahtan/OULA`. It is **not** the Lovable expo simulator.

The root now opens `/gateway`. The connected workspace is `/workspace`, with the prior demonstration at `/journey`. See `../../docs/delivery/CONNECTED_WORKSPACE_RUNBOOK.md` for startup and `SCENARIO_ACCEPTANCE.md` for scenario coverage.

The presentation browser experience remains explicitly DEMO with fictional Riyadh fixture data. A separate, gated `DEV_AUTH` proof mode can now exercise the canonical Java/PostgreSQL Golden Path with a locally signed development JWT. DEV_AUTH is not production and must never be presented as a live external integration.

## Runtime modes

### DEMO — default

No backend session is acquired. The visible property cards, local LifeFit preview, browser Deal Room projection, Guardian signal and agent trace remain fictional/simulated and are labeled accordingly.

### DEV_AUTH — integration proof only

Enable only for local/CI integration:

```bash
NEXT_PUBLIC_OULA_DEV_AUTH=true
NEXT_PUBLIC_OULA_API_BASE_URL=http://127.0.0.1:8080
```

The backend must run with `SPRING_PROFILES_ACTIVE=dev-auth`. In this mode the `/journey` screen exposes a dedicated **DEV_TEST_ONLY** proof panel. It keeps the signed bearer token in React memory only and runs:

1. fixed fictional buyer fixture token;
2. explicit life-context consent;
3. canonical Create Intent;
4. dev-only preparation of fictional matching signals;
5. canonical MatchRun;
6. canonical Property Passport;
7. canonical Transaction create;
8. canonical QUALIFIED transition;
9. canonical human-confirmed Viewing;
10. canonical human-confirmed Offer;
11. capability-aware Deal Room read.

The fixture preparation endpoint exists only in the dev-auth profile. Matching, Passport and Transaction APIs are the same canonical endpoints used by the real backend.

No silent LIVE -> DEMO fallback exists.

## Governed personal agent preview

The six-world agent dock in `components/oula-personal-agent.tsx` uses `lib/personal-agent.ts` for limited deterministic local navigation. It is visibly DEMO-only, stores its trace only in ephemeral React state, does not send prompts to an AI provider or invoke the Java backend, and hard-blocks sensitive signing/payment/ownership commands in preview UX. It is **not** an authorization layer or the future authenticated Agent Runtime.

See `../../docs/architecture/OULA_PERSONAL_AGENT_RUNTIME.md` for body mapping, privacy/authority controls and P1 dependencies.

## Development

Requirements: Node.js 22+, Java 21, PostgreSQL/PostGIS and reviewed dependency installation.

Normal frontend:

```bash
npm ci
npm run typecheck
npm test
npm run build
npm run dev
```

Real backend integration proof:

1. Follow `../../docs/delivery/DEV_AUTH_RUNBOOK.md` to start Java with `dev-auth`.
2. Build the frontend with the two `NEXT_PUBLIC_*` variables shown above.
3. Run `npm run test:integration` for the browser-to-database proof.

CI workflow: `.github/workflows/browser-integration.yml`.

## Integration rules

Backend API source: `../../contracts/openapi.yaml`.

- OIDC bearer claims + workspace + purpose + scope remain server-authoritative.
- Consent does not replace authorization.
- Do not treat local preview score as canonical LifeFit.
- Real property/deal stages are sourced from Java.
- Do not replace failed backend requests with unmarked demo data.
- No payment, signing, bank, escrow, title-transfer or REGA/government integration is created by DEV_AUTH.

## Workspaces

`apps/web` owns user-facing views and session presentation. `src/main/java/com/oula/**` owns domain facts, transactions, policies and intelligence.

Keep task boundaries; refer to #41, #40, #51, #52 and release train #38.

### LifeFit score interpretation

Matching scores and dimensions come from the Java API. New match runs use
`lifefit-v2-decimal`: decimal arithmetic with final two-decimal HALF_UP rounding.
The API response shape is unchanged. The UI must not recompute scores or treat
`confidence` as a calibrated probability, valuation, title verification or
permission to execute a transaction. It remains a property-truth coverage
heuristic. Historical v1 recommendations retain their recorded model identity.
See `docs/delivery/LIFEFIT_DECIMAL_CORRECTNESS_2026-10-17.md` at repository root.

### Material recommendation evidence

Material recommendation generation and human decision recording require linked
current `VERIFIED` evidence with recorded source identity/reference and hash.
Evidence can expire or be rejected after a recommendation; the API can therefore
refuse a later decision. The UI must surface that refusal and must not silently
substitute another evidence item or create a different idempotency key.
`VERIFIED` remains a stored status supplied through the current authorized
registration API; it is not proof of independent source attestation.
