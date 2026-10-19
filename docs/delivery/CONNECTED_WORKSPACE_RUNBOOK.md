# OULA connected workspace — local candidate

This delivery uses the canonical OULA Java application, existing 30 Flyway migrations and PostgreSQL/PostGIS. No ROS code, external bank/government simulator or separate backend is introduced.

## Start

Prerequisites: Git, Docker Desktop with Compose, Java 21, Maven and Node.js 22+.

```bash
git clone https://github.com/M-Qahtan/OULA.git
cd OULA
git checkout codex/rebuild-cinematic-worlds-20261019
bash scripts/start-local-expo.sh
```

Open `http://127.0.0.1:3000/`. The root opens the five-family gateway. Each family links to the shared connected workspace at `/workspace`. The previous demonstration remains available at `/journey`. Technical proof routes remain available for engineering regression.

The startup script binds the database, Java API and Next.js to loopback, enables the existing `dev-auth` backend profile, generates a session secret without printing it and installs the locked frontend dependencies with `npm ci`. Stop with Ctrl+C; the API and web processes stop. The Docker database volume is retained. Starting the script again retains saved domain records, while signing in obtains a new development identity session. No destructive reset is run.

## Buyer acceptance journey

1. Open `/workspace`, select the local buyer session in the assistant panel, and select Workspace mode.
2. Open My intent. Explicitly accept and record life-context processing consent.
3. Enter a budget, bedroom requirement and preferred district. Save the intent and run matching.
4. Open a property passport. Property information remains DECLARED/DEV_FIXTURE with 0% verified-fact coverage.
5. Review the selection. A material intelligence recommendation requires existing, eligible, source-authorized evidence. No verified evidence is seeded to bypass that condition.
6. Continue with the user's own choice into Deal room. Confirm and create a transaction draft.
7. Confirm qualification, then confirm a viewing request, then enter and confirm an offer.
8. Copy the saved transaction reference, reload the page, and read the transaction again. The viewing and offer come from Java/PostgreSQL.

A recorded viewing is not a confirmed appointment. A submitted offer is not counterparty acceptance, signing, payment or transfer of title.

## Owner / manager acceptance journey

1. Select the local property-manager session. HABITAT opens Add a property.
2. Enter a residential/commercial property, district, bedrooms and asking price.
3. Confirm the declaration and save. The frontend reads the saved record back from Java.
4. Confirm and save a private SALE/RENT listing draft. Its state remains DRAFT; it is not published.
5. Reload and reopen the saved property reference. Development-session creations always carry DEMO provenance and DECLARED truth.

6. Open HABITAT → Obligations and deadlines → Add an obligation. Enter the saved property reference in the connected workspace.
7. Explicitly confirm enrollment, then enter a declared obligation title, local due time and priority, confirm and save.
8. Confirm deadline assessment. Review the returned action, enter your resolution note, confirm and record resolution.
9. Reload and read the care record again. The saved states come from Java/PostgreSQL: obligation `SATISFIED`, signal `RESOLVED`, action `COMPLETED`.

Property care also supports authorized reads of existing vitals/advice by property reference. Creating an intake record does not automatically enroll a property in management or create operational evidence. Recording a resolution remains a human declaration, not a certified inspection. The fixed fictional property-manager fixture has action-completion permission only in the local `dev-auth` profile; production account grants are not changed.

## Authentication and security

Normal OIDC uses Authorization Code + PKCE, state and nonce; signed ID/access tokens are validated. Access tokens stay in an encrypted HttpOnly cookie and are never returned in the public session, URLs or browser storage. Sessions expire within one hour and never outlive the access token. There is no automatic token refresh or provider-wide logout/revocation in this delivery.

The explicit local identity path is available only when `OULA_LOCAL_DEV_AUTH=true`, HTTP loopback is enabled, and backend/app/issuer are all literal HTTP loopback URLs. The Java service must independently validate the signed fixture before the BFF creates a cookie. Disabling local identity also denies previously issued DEV_TEST_ONLY sessions at the BFF. This path is not an OIDC fallback.

The BFF forwards only allowlisted contracts, chooses purpose/scope from the contract, ignores browser-supplied authority headers, validates request/response shapes, rejects cross-origin mutations and enforces workspace/nested transaction identity. Idempotency keys survive identical retries. Conflicts remain visible; no local stage advancement conceals a rejected server command.

For a real identity provider, configure the server-only values in `apps/web/.env.example`, turn both local flags off, configure the matching Java issuer/audience/JWKS, and validate that provider's deployment. The synthetic OIDC harness proves protocol behavior; it is not evidence of an approved production identity deployment.

## Verification

`bash scripts/verify-local.sh` automates the checks below using a separate disposable Compose project on database port 55432. Stop an existing local API first; the script refuses to reuse a responding API on port 8080. It does not remove the demonstration database volume.

```bash
# With an isolated PostgreSQL/PostGIS test database configured using DB_URL/DB_USER/DB_PASSWORD:
CI_DB_TEST=true mvn -B -ntp verify
cd apps/web
npm ci
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
# With the canonical Java API running under dev-auth, build with the proof UI enabled:
NEXT_PUBLIC_OULA_DEV_AUTH=true NEXT_PUBLIC_OULA_API_BASE_URL=http://127.0.0.1:8080 npm run build
npm run test:integration
```

Use a dedicated test database. Browser fixtures create fictional records. Tests must not target a production database.

## Release boundary

This is a local integrated candidate. Production identity onboarding, licensed property data, generative AI provider access, government/bank/payment/signature integrations, engineering simulation engines and city planning engines are not activated. The reference design images are preserved as illustrative assets; the implementation does not claim pixel-identical certification from raster references. Maintainer review and the repository release gates remain required for merging and production release.

Rollback is a revert of the delivery commit. No new database migration or destructive schema operation is included.
