# OULA — Real Frontend (authoritative repository)

This is the **real OULA web frontend source** inside `M-Qahtan/OULA`. It is **not** the existing Lovable expo simulator. Current foundation is a runnable preview-only Arabic-first UI with deliberately fictional Riyadh fixture data and an explicitly unauthenticated API boundary. It is NOT a true backend-integrated, real-estate-ready product yet.

## Governed personal agent preview (P0)
The six-world agent dock in `components/oula-personal-agent.tsx` uses `lib/personal-agent.ts` for limited deterministic local navigation. It is visibly DEMO-only, stores its trace only in ephemeral React state, does not send prompts to an AI provider or invoke the Java backend, and hard-blocks sensitive signing/payment/ownership commands in preview UX. It is **not** an authorization layer or the future authenticated Agent Runtime. See `../../docs/architecture/OULA_PERSONAL_AGENT_RUNTIME.md` for body mapping, privacy/authority controls and P1 dependencies.

## Development
Requirements: supported Node.js 22+ and a secure package install after reviewing versions. From `apps/web`, run `npm install`, `npm run typecheck`, `npm test`, `npm run build`, then `npm run dev`.

## Integration rules
Backend API source: `../../contracts/openapi.yaml`. Server-side OIDC session and token acquisition must be built in coordination with issue #46/#41 before LIVE mode can be enabled. Do not treat mock score as canonical LifeFit. Real property listing and deal stages are sourced from Java. Do not replace failed backend requests with unmarked demo data. No payment/bank/REGA integration exists here.

## Workspaces
`apps/web` owns user-facing views and session presentation; `src/main/java/com/oula/**` owns all domain facts, transactions, policies and intelligence. Keep task boundaries; refer to #40, #51, #52 and release train #38.
