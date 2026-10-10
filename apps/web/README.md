# OULA — Real Frontend (authoritative repository)

This is the **real OULA web frontend source** inside `M-Qahtan/OULA`. It is **not** the existing Lovable expo simulator. Current foundation is a runnable preview-only Arabic-first UI with deliberately fictional Riyadh fixture data and an explicitly unauthenticated API boundary. It is NOT a true backend-integrated, real-estate-ready product yet.

## Development
Requirements: supported Node.js 22+ and a secure package install after reviewing versions. From `apps/web`, run `npm install`, `npm run typecheck`, `npm test`, `npm run build`, then `npm run dev`.

## Integration rules
Backend API source: `../../contracts/openapi.yaml`. Server-side OIDC session and token acquisition must be built in coordination with issue #46/#41 before LIVE mode can be enabled. Do not treat mock score as canonical LifeFit. Real property listing and deal stages are sourced from Java. Do not replace failed backend requests with unmarked demo data. No payment/bank/REGA integration exists here.

## Workspaces
`apps/web` owns user-facing views and session presentation; `src/main/java/com/oula/**` owns all domain facts, transactions, policies and intelligence. Keep task boundaries; refer to #40, #51, #52 and release train #38.
