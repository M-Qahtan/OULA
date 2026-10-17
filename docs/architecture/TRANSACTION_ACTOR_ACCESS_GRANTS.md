# Transaction Actor-Level Grants (Development Implementation)

## Purpose and exact security boundary
A workspace membership and an OAuth scope do not automatically grant access to **every**
deal inside that workspace. The new durable `tx.actor_deal_grant` table distinguishes:
- **ORIGINATOR**: may read and execute already-authorized transaction commands.
- **VIEWER**: may read only, if the JWT also carries `oula.transaction.read`.
  Holding `oula.transaction.advance` does **not** promote the VIEWER.

Grants also require a matching workspace, actor UUID, active status and unexpired time.
Deal GET and deal room now fail closed for nonparticipants, including users in the
same workspace with transaction.read scopes. All write commands check originator
status prior to executing or replaying them.

## Owner-controlled delegation
`POST /v1/transactions/{transactionId}/read-grants` with
`{ "actorId": "uuid", "expiresAt": "ISO-8601 UTC" }` needs both
`oula.transaction.grant.manage` and the verified transaction ORIGINATOR.
New viewer invitations are **PENDING**: the recipient cannot read any deal
until that actor explicitly accepts through
`POST /v1/transactions/{transactionId}/read-grants/accept` using a signed
bearer with its own actor_id, workspace, purpose and `oula.transaction.read`.
The response distinguishes `active` (not revoked) from `accepted`
(permission effective). `active=true,accepted=false` is still denied.
Viewer grants expire within at most 90 days. The same owner may later revoke via
`POST /v1/transactions/{transactionId}/read-grants/{actorId}/revoke`.
Invite, accept and revoke are actor-scoped, atomic idempotent operations with
outbox audit events. Duplicate acceptance using the same key is replayed only
while the invitation remains active and unexpired; fresh keys do not renew it.
No privilege to grant transaction mutations, sign, pay or change ownership.

## Migration
V024 adds `accepted_at`; existing ORIGINATOR records are marked accepted.
Existing VIEWER grants stay unaccepted until their specific recipient
explicitly accepts (fail closed rather than silently grandfathering disclosure).
V023 creates workspace-constrained grants and *only* backfills originators from
recorded transaction creation history (null from_stage). Historical transactions
without provenance receive no automatic grants; reconcile them with trusted
evidence before enabling access. No blanket workspace-wide migration.

## Demo and limitations
`DEV_TEST_ONLY` broker preview creates a fictional deal under a separate synthetic
originator, creates a 14-day VIEWER invitation and accepts it
on demand inside an isolated DEV_TEST_ONLY fixture. This bypass must never be
enabled for real accounts. The broker remains read only. The broker
can read PostgreSQL-backed Deal Room but never mutate it.

This does **not** establish identity of a real broker or verify an underlying
brokerage mandate, property title or counterparty representation. A grant targets
an actor UUID: real operation requires verified organizational membership,
counterparty invitation acceptance and legally appropriate mandates.
Check regulatory constraints and privacy, retention, ownership and tenant visibility
separately before production launch.
