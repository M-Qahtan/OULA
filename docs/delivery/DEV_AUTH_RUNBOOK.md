# OULA Local Dev/Test Identity Runbook

This runbook exists only to exercise the real OAuth2 Resource Server locally.
It does **not** choose or emulate a production identity provider.

## Safety boundary

- Use only the `dev-auth` profile.
- Never combine `dev-auth` with `prod`.
- No client secret is embedded in the browser.
- Fixtures are fictional and source-controlled.
- Tokens are short-lived and signed with an ephemeral RSA key generated at startup.
- The browser cannot request arbitrary actor IDs, workspaces, purposes or scopes.
- CORS is restricted to explicitly configured origins.

## Start the backend locally

Required PostgreSQL/PostGIS instance:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/oula
export DB_USER=oula
export DB_PASSWORD=oula
export SPRING_PROFILES_ACTIVE=dev-auth
export OULA_CORS_ALLOWED_ORIGINS=http://localhost:3000
mvn spring-boot:run
```

No `OIDC_ISSUER_URI` or external JWK endpoint is needed in this profile because
the profile contributes its own local `JwtDecoder`.

## Inspect fictional fixtures

```bash
curl -s http://localhost:8080/dev-auth/fixtures
```

Current fixture aliases:

- `buyer` — PROPERTY_DECISION_SUPPORT + TRANSACTION_EXECUTION.
- `property-manager` — PROPERTY_MANAGEMENT.

These are integration identities only, not real OULA users.

## Issue a local signed token

```bash
TOKEN=$(curl -s \
  -H 'Content-Type: application/json' \
  -d '{"fixture":"buyer"}' \
  http://localhost:8080/dev-auth/token | jq -r .accessToken)
```

The response is marked `DEV_TEST_ONLY` and `Cache-Control: no-store`.

## Call a protected API

The buyer fixture workspace is:

`20000000-0000-4000-8000-000000000001`

Read current consent decisions:

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-OULA-Workspace-ID: 20000000-0000-4000-8000-000000000001' \
  -H 'X-OULA-Purpose: PROPERTY_DECISION_SUPPORT' \
  http://localhost:8080/v1/consents
```

Grant life-context processing consent:

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-OULA-Workspace-ID: 20000000-0000-4000-8000-000000000001' \
  -H 'X-OULA-Purpose: PROPERTY_DECISION_SUPPORT' \
  -H 'Idempotency-Key: local-consent-grant-001' \
  -H 'Content-Type: application/json' \
  -d '{"consentCode":"LIFE_CONTEXT_PROCESSING","decision":"GRANTED","reason":"Local integration test"}' \
  http://localhost:8080/v1/consents/decisions
```

Revoke with a **new** idempotency key:

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-OULA-Workspace-ID: 20000000-0000-4000-8000-000000000001' \
  -H 'X-OULA-Purpose: PROPERTY_DECISION_SUPPORT' \
  -H 'Idempotency-Key: local-consent-revoke-001' \
  -H 'Content-Type: application/json' \
  -d '{"consentCode":"LIFE_CONTEXT_PROCESSING","decision":"REVOKED","reason":"Local integration test"}' \
  http://localhost:8080/v1/consents/decisions
```

## Expected failure cases

These must fail closed:

- no bearer token -> `401 application/problem+json`;
- workspace not present in the token -> `403`;
- purpose not present in the token -> `403`;
- missing OAuth scope -> `403`;
- same idempotency key with a different consent command -> `409`;
- browser origin outside the CORS allowlist -> rejected by the CORS boundary.

## Production boundary

Production continues to require configured external OIDC values:

- `OIDC_ISSUER_URI`;
- `OIDC_JWK_SET_URI`;
- `OIDC_AUDIENCE`.

The `/dev-auth/**` routes do not exist unless the `dev-auth` profile is active,
and the development components are disabled when `prod` is active.
