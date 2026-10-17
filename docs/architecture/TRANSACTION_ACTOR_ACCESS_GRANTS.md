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
Viewer grants expire within at most 90 days. The same owner may later revoke via
`POST /v1/transactions/{transactionId}/read-grants/{actorId}/revoke`.
Both are actor-scoped, atomic idempotent operations with outbox audit events.
No privilege to grant transaction mutations, sign, pay or change ownership.

## Migration
V023 creates workspace-constrained grants and *only* backfills originators from
recorded transaction creation history (null from_stage). Historical transactions
without provenance receive no automatic grants; reconcile them with trusted
evidence before enabling access. No blanket workspace-wide migration.

## Demo and limitations
`DEV_TEST_ONLY` broker preview creates a fictional deal under a separate synthetic
originator and delegates a 14-day VIEWER grant to the broker fixture. The broker
can read PostgreSQL-backed Deal Room but never mutate it.

This does **not** establish identity of a real broker or verify an underlying
brokerage mandate, property title or counterparty representation. A grant targets
an actor UUID: real operation requires verified organizational membership,
counterparty invitation acceptance and legally appropriate mandates.
Check regulatory constraints and privacy, retention, ownership and tenant visibility
separately before production launch.
