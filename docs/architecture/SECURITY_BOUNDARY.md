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


## Integration Trust Fabric

External partners are not OULA users and do not enter domain services as arbitrary HTTP payloads.

Wave 11 introduces:
- a workspace-scoped integration partner identity;
- explicit verification evidence;
- auth-mode and credential-reference metadata without storing raw credentials;
- exact purpose/operation/resource contracts;
- allowed data-class subsets;
- fresh VerifiedExternalPrincipal objects produced only by authenticated transport adapters;
- partner + external-event replay protection;
- raw-payload exclusion from the inbound receipt table.

No public raw webhook endpoint is opened by Wave 11. A concrete mTLS, JWS, signed-webhook or OAuth adapter must authenticate the transport and bind the credential before constructing VerifiedExternalPrincipal.

An outbound request in PREPARED state is metadata only. It does not prove that a network request was sent or acknowledged.


## Property Vital Signs scopes

Wave 12 exposes deterministic property-health observations under the existing `PROPERTY_MANAGEMENT` purpose:

- `oula.property.vitals.read`
- `oula.property.vitals.assess`

The assessment may also be invoked internally under `AUTONOMOUS_EXECUTION` through a trusted internal seam, but it is observational only: it cannot approve budgets, assign providers, move funds, alter Property Facts, or execute external actions. The public HTTP endpoints remain human PROPERTY_MANAGEMENT scoped.

## Tenancy and Occupancy scopes — Wave 13

All current HTTP mutations require workspace membership, the explicit
PROPERTY_MANAGEMENT purpose and one of these independent OAuth scopes:

- oula.tenancy.read
- oula.tenancy.write (unit, draft creation, cancellation)
- oula.tenancy.sign (verified contract evidence)
- oula.tenancy.activate (materialize rent schedule and Guardian expiry review)
- oula.tenancy.occupancy.write (evidence-backed check-in/check-out)
- oula.tenancy.end (verified termination, after check-out)
- oula.tenancy.renewal.write (append-only human renewal decisions)

No autonomous agent receives implied signature or termination authority.
Verified document evidence is workspace- and type-scoped.
OULA does not assert government registration, execute rental payments, or verify
party legal identities in this wave.

## Wave 16 — Documentary Rent and Occupancy Scopes

- oula.tenancy.finance.read — read rent evidence coverage and documentary gaps.
- oula.tenancy.finance.record — append an evidence-backed receipt observation.
- oula.tenancy.finance.reverse — append a linked verified reversal, never edit the original.
- oula.tenancy.occupancy.read — read dated recorded occupancy with explicit coverage.

All scopes require the existing PROPERTY_MANAGEMENT purpose and workspace-bound JWT claims. Financial observations require workspace-matched VERIFIED evidence of type RENT_RECEIPT or RENT_RECEIPT_REVERSAL; this is not an independent bank confirmation. No automated funds movement, debt enforcement or legal determination is authorized.

## Wave 17 — Rental Lifecycle Advice

- `oula.property.rental-advisory.read` — read reason-coded, evidence-grounded
  rental review proposals for a workspace-controlled property.
- PROPERTY_MANAGEMENT purpose is mandatory.
- All data comes through canonical tenancy public services; the advisory
  module owns no tenancy tables and cannot mutate lease, payment, Guardian
  obligations, cases, facts, events, legal notices or external systems.
- Recommendations always carry HUMAN_REVIEW_REQUIRED.
- Documentary evidence gaps are **not** bank-confirmed debt or a legal
  delinquency determination. Recorded vacancy is **not** a live occupancy sensor.

## Wave 19 — Rental Human Advisory Review Permissions

Distinct, PROPERTY_MANAGEMENT purpose-bound scopes:

- oula.advisory.review.capture — capture a source-matched rental proposal
- oula.advisory.review.decide — append a human disposition, never an execution grant
- oula.advisory.review.observe — append a workspace-matched VERIFIED
  ADVISORY_OUTCOME documentary observation
- oula.advisory.review.read — read frozen source/timeline/non-causal coverage counts

Wave 18 interventions observe operational Vital Signs; this wave tracks
rental-specific human documentary outcomes. Neither permits a legal or bank
assertion, automatic learning or uncontrolled Work Order execution.

## Wave 20 — Decision Evaluation

- `oula.property.decision-evaluation.read`: read evidence coverage and
  research-readiness caveats over authorized property operational and rental
  review data under PROPERTY_MANAGEMENT.
- The evaluation module has read-only dependencies on public advisory and
  interventions APIs, no database tables or outgoing events.
- No autonomous training, algorithm promotion, causal efficacy assertion,
  financial or legal decision, or change to Property Truth is permitted.
