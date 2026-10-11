# OULA — Frontend / Backend Source-of-Truth Integration Contract

## 1. Authority

The authoritative product is the canonical GitHub repository:

- Backend contract: `contracts/openapi.yaml` — current declared API version `1.10.0`.
- Backend behavior and authorization: Java controllers and domain services under `src/main/java/com/oula`.
- Real frontend: `apps/web`.
- Lovable / Oula LifeFlow: presentation simulator only; it is never a source of production API truth.

The machine-readable companion to this document is:

`docs/delivery/frontend-backend-contract-matrix.json`

CI verifies that contracted rows still exist in OpenAPI, typed rows still exist in the frontend adapter, declared scopes still exist in Java, and explicitly missing transaction commands remain missing until the backend intentionally adds them.

## 2. Release language

A capability may be described as **REAL** only when all of the following are true:

1. the backend contract exists;
2. authorization and workspace/purpose enforcement exist;
3. the browser has a real authenticated session;
4. the request reaches the Java backend;
5. data is persisted/read through the canonical database path;
6. positive and negative integration tests pass;
7. the browser shows the server result without silently substituting fictional data.

A feature can therefore have a real backend contract while the current Expo screen is still **DEMO** or **SANDBOX**.

## 3. Contract status vocabulary

| Status | Meaning |
|---|---|
| `CONTRACTED_TYPED` | OpenAPI + Java endpoint exist and `apps/web/lib/api.ts` has a typed adapter. |
| `CONTRACTED_UNWIRED` | Backend contract exists, but the real browser has no adapter/session integration yet. |
| `CONTRACTED_SCHEMA_GAP` | Endpoint exists, but OpenAPI does not yet declare the response schema required for a safe typed web integration. |
| `RESOURCE_SERVER_ONLY` | OAuth2/OIDC backend boundary exists, but browser login/session integration is not yet wired. |
| `MISSING` | Desired journey command is absent from current OpenAPI and must not be invented by the frontend. |
| `FUTURE` | Research/horizon capability; no production endpoint is claimed. |

## 4. Golden-journey matrix

| UI journey | Status | Canonical API | Purpose / scope | Web state |
|---|---|---|---|---|
| Sign-in / Workspace | RESOURCE_SERVER_ONLY | OAuth2/OIDC Resource Server; no browser session contract | JWT + workspace + purpose + scope | DEMO; blocker #46/#41 |
| Create Intent | CONTRACTED_TYPED | `POST /v1/intents` · `createIntent` | PROPERTY_DECISION_SUPPORT · `oula.intent.write` | Typed seam; screen still DEMO |
| Read Intent | CONTRACTED_TYPED | `GET /v1/intents/{intentId}` · `getIntent` | PROPERTY_DECISION_SUPPORT · `oula.intent.read` | Typed seam; screen still DEMO |
| Run Matching | CONTRACTED_TYPED | `POST /v1/intents/{intentId}/matches` · `runMatching` | PROPERTY_DECISION_SUPPORT · `oula.matching.run` | Typed seam; local `previewMatch` remains illustrative only |
| Property Passport | CONTRACTED_TYPED | `GET /v1/properties/{propertyId}/passport` · `getPropertyPassport` | PROPERTY_DECISION_SUPPORT or PROPERTY_MANAGEMENT · `oula.property.passport.read` | Typed seam; visible fixture still DEMO |
| Generate recommendation | CONTRACTED_UNWIRED | `POST /v1/intelligence/recommendations` | PROPERTY_DECISION_SUPPORT · `oula.intelligence.recommend` | DEMO |
| Explain recommendation | CONTRACTED_UNWIRED | `GET /v1/intelligence/recommendations/{recommendationId}/explain` | PROPERTY_DECISION_SUPPORT · `oula.intelligence.read` | DEMO |
| Record human decision | CONTRACTED_UNWIRED | `POST /v1/intelligence/recommendations/{recommendationId}/decisions` | PROPERTY_DECISION_SUPPORT · `oula.intelligence.decide` | DEMO |
| Start transaction | MISSING | desired `POST /v1/transactions` is absent | TRANSACTION_EXECUTION | SANDBOX; blocker #39/#49 |
| Request viewing | MISSING | desired dedicated viewing command absent | TRANSACTION_EXECUTION | SANDBOX; blocker #39/#49 |
| Submit offer | MISSING | desired dedicated offer command absent | TRANSACTION_EXECUTION | SANDBOX; blocker #39/#49 |
| Read transaction | CONTRACTED_TYPED | `GET /v1/transactions/{transactionId}` | TRANSACTION_EXECUTION · `oula.transaction.read` | Typed seam; Deal Room still SANDBOX |
| Advance transaction | CONTRACTED_TYPED | `POST /v1/transactions/{transactionId}/transitions` | TRANSACTION_EXECUTION · `oula.transaction.advance` | Typed seam; requires expectedVersion + Idempotency-Key |
| Guardian overview | CONTRACTED_SCHEMA_GAP | `GET /v1/properties/{propertyId}/management` | PROPERTY_MANAGEMENT · `oula.property.management.read` | SANDBOX; response schema must be added |
| Guardian assessment | CONTRACTED_SCHEMA_GAP | `POST /v1/properties/{propertyId}/guardian/assess` | PROPERTY_MANAGEMENT · `oula.property.guardian.run` | SANDBOX; response schema must be added |
| Latest Property Vitals | CONTRACTED_UNWIRED | `GET /v1/properties/{propertyId}/vitals/latest` | PROPERTY_MANAGEMENT · `oula.property.vitals.read` | SANDBOX |
| Operational advice | CONTRACTED_UNWIRED | `GET /v1/properties/{propertyId}/operational-advice` | PROPERTY_MANAGEMENT · `oula.property.advisory.read` | SANDBOX; human-review only |
| Reality case | CONTRACTED_UNWIRED | `GET /v1/intelligence/outcomes/{outcomeId}/reality-case` | PROPERTY_DECISION_SUPPORT · `oula.intelligence.read` | DEMO |
| Reality timeline | CONTRACTED_UNWIRED | `GET /v1/intelligence/outcomes/{outcomeId}/timeline` | PROPERTY_DECISION_SUPPORT · `oula.intelligence.read` | DEMO |
| City / Built World simulation | FUTURE | none claimed | — | FUTURE |

The JSON companion is normative for machine checks and contains operation IDs, schema names, idempotency flags, blockers and response-code notes.

## 5. Authentication, workspace and purpose

OULA is fail-closed.

A bearer token is necessary but not sufficient. The server independently validates:

- `sub`;
- OULA `actor_id`;
- allowed `oula_workspace_ids`;
- allowed `oula_purposes`;
- OAuth `scope`.

`X-OULA-Workspace-ID` and `X-OULA-Purpose` are request declarations. They do **not** grant authority by themselves.

The web application must never ship a client secret. A real browser session may only be enabled when the development/production IdP flow is explicitly configured and reviewed.

## 6. Idempotency and concurrency

Material mutations declared idempotent in the matrix require `Idempotency-Key`.

The transaction transition also requires `expectedVersion`. A stale write is a `409 Conflict` and must be shown as a conflict/reload condition; the frontend must not hide it by advancing local state.

Same idempotency key + different request is also a conflict. The browser must not auto-generate a new key to conceal a conflict.

## 7. Provenance and truth

The frontend must preserve, not reinterpret, backend truth semantics.

- `PropertyPassportFact.valueJson` is JSON **text** in the current contract. The browser may render it only through an explicit presentation adapter; parsing it does not make its content verified.
- `truthStatus` remains server-provided. The current OpenAPI does not define it as a closed enum, so the TypeScript boundary does not narrow it.
- `verifiedFactCoverage` is the fraction of facts marked VERIFIED. It is not confidence, title proof, valuation certification or legal certainty.
- A browser fixture is DEMO/SANDBOX even when its interaction is fully functional.
- No bank confirmation, public registry result, escrow, government contract, certified valuation or sensor reading may be fabricated.

## 8. Error behavior

There is no automatic LIVE → DEMO fallback.

The UI must distinguish at least:

- authentication required;
- permission/purpose/scope denied;
- resource not found;
- validation failure;
- stale/conflicting write;
- unavailable backend;
- schema/contract mismatch.

Retry is allowed only when the operation is safe/idempotent and the same business command remains semantically identical.

## 9. Known P0 contract gaps

The frontend is prohibited from creating these routes locally because they do not exist in the current OpenAPI:

- transaction creation;
- dedicated viewing request;
- dedicated offer submission.

These are tracked under #39 and #49.

The browser OIDC/session/workspace integration is also not complete and remains under #46/#41.

Until those gaps close, the 3-minute Expo journey cannot be described as an authenticated end-to-end Java/PostgreSQL transaction path.

## 10. Contract drift gate

`apps/web/tests/contract-matrix.test.ts` protects the matrix by checking:

- every `CONTRACTED_*` operation is present in OpenAPI;
- every `CONTRACTED_TYPED` adapter exists in `apps/web/lib/api.ts`;
- declared OAuth scopes are present in the relevant Java API sources;
- every `MISSING` path is still absent from OpenAPI;
- protected contracted endpoints declare workspace/purpose usage;
- no current browser journey row is marked LIVE before authenticated integration is proven.

This gate does not replace authenticated integration testing. It prevents documentation and adapter drift while #41/#46/#49 are completed.
