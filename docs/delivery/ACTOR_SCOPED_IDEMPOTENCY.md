# Actor-scoped Idempotency Hardening — OULA

**Status:** source-level defensive hardening, CI acceptance pending.

## Threat
A retry key was unique only to a workspace. Some application controllers fingerprinted
only operation and payload. A different actor with a valid workspace-specific write scope
who learned/reused that key could receive a previous actor's completed command response
instead of receiving a conflict, even though they passed routine authorization.

## Mitigation
For the **buyer Intent + Matching**, **private Property/Listing intake**,
**Guardian/Property management**, and **Transaction Deal Room** write endpoints,
derive the receipt fingerprint as:

`SHA256("OULA_ACTOR_SCOPED_IDEMPOTENCY_V1", verified-workspace,
 verified-actor-id, verified-purpose, original-request-fingerprint)`

The normal idempotency DB key stays `(workspace_id,idempotency_key)`: two actors
colliding on a key produce **409** rather than exposing or replacing the first result.
A same-actor/same-purpose/same-payload retry still replays the original response.
A renewed token with the same verified actor identity retains retry semantics.

## Guarantees and limitations
- JWT auth, workspace membership, purpose and per-operation scope remain enforced
  independently before the idempotency lookup.
- Existing atomic transaction around claim/mutation/receipt remains unchanged.
- This is not a broad authorization system: other controller families need separate
  evaluation before claiming full API-wide actor replay isolation.
- The new hash version will intentionally conflict with old unbound receipts for the
  same key. Prior production data is not silently reinterpreted; assess migration
  and retention before any production upgrade.
- This safeguard cannot reverse a committed external payment, government action,
  or network side effect. Such workflows require outbox/partner-level deduplication
  and governance.
- Fixture and MVP demonstrations are not evidence of verified ownership or finance.

## Required checks
1. Java/JUnit deterministic actor/workspace/purpose binding and negative MockMvc tests.
2. Existing PostGIS/transaction atomicity and concurrent-key tests.
3. Frontend build / Browser Integration / CodeQL on exact branch head.
