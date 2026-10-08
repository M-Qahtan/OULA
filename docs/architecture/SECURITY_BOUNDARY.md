# OULA Security & API Boundary

## Security posture

OULA uses a fail-closed OAuth 2.1 / OIDC Resource Server boundary. A valid bearer token is necessary but not sufficient.

Every protected operation evaluates four independent dimensions:

1. **Identity** — who is acting (`sub` + OULA `actor_id`).
2. **Workspace** — which tenant/workspace the action is operating inside.
3. **Purpose** — why the data/action is being used.
4. **Scope** — what API capability the authorization server granted.

A request header never grants authority by itself. `X-OULA-Workspace-ID` and `X-OULA-Purpose` are declarations that must match JWT claims.

## Canonical JWT claims

| Claim | Meaning |
|---|---|
| `sub` | Authorization-server subject |
| `actor_id` | OULA UUID for the acting person/system |
| `oula_workspace_ids` | Workspaces the token may operate in |
| `oula_purposes` | Approved purpose limitations |
| `scope` | OAuth capabilities mapped to `SCOPE_*` authorities |

Initial scopes:

- `oula.matching.run`
- `oula.transaction.read`
- `oula.transaction.advance`

Initial purposes:

- `PROPERTY_DECISION_SUPPORT`
- `TRANSACTION_EXECUTION`

Purpose is deliberately separate from role. A powerful role does not imply unrestricted use of data.

## Resource server validation

Runtime deployments must provide:

- `OIDC_ISSUER_URI`
- `OIDC_JWK_SET_URI`
- `OIDC_AUDIENCE` (defaults to `oula-api`)

No production identity provider is hard-coded in source.

## HTTP idempotency

State-changing endpoints require `Idempotency-Key`.

The ledger is scoped by workspace and binds a key to:

- operation,
- canonical request fingerprint,
- state,
- response.

Rules:

- same key + same request => replay the stored response;
- same key + different request => `409 Conflict`;
- in-progress key => `409 Conflict` + `Retry-After`;
- failed domain command => claim is released.

Correlation identifiers are deterministic UUIDv8 values derived from a domain-separated SHA-256 digest of operation + workspace + idempotency key. This gives retries the same correlation identity without exposing raw keys.

HTTP idempotency is not advertised as distributed exactly-once delivery. Safety comes from the combination of the ledger plus domain invariants such as unique MatchRun correlation and optimistic transaction versions.

## Outbox

Material state changes write an event to `platform.outbox_event` inside the same database transaction.

The first transport is the in-process Spring event bus because OULA is intentionally a Modular Monolith. The `EventSink` port allows Kafka or another durable broker to be introduced later without making the domain depend on transport infrastructure.

The dispatcher uses `FOR UPDATE SKIP LOCKED` so multiple workers can drain the outbox without selecting the same rows.

## Fail-closed examples

OULA denies the request when:

- JWT is missing or invalid;
- `actor_id` is absent or malformed;
- workspace header is not present in the token;
- requested purpose is not approved by the token;
- purpose is wrong for the operation;
- required OAuth scope is absent;
- transaction belongs to another workspace;
- an idempotency key is reused for different input;
- a stale transaction version attempts to overwrite newer state.


## Property operations execution scopes

Wave 07 keeps real-world execution behind the existing `PROPERTY_MANAGEMENT` purpose and adds granular capabilities:

- `oula.property.workorder.read`
- `oula.property.workorder.write`
- `oula.property.workorder.approve`
- `oula.property.workorder.execute`
- `oula.property.workorder.verify`

A provider assignment does not grant approval authority. Completion submission does not grant verification authority. The API requires an explicit human-authorized approval before execution and a separate verification scope before the linked Guardian action is resolved.
