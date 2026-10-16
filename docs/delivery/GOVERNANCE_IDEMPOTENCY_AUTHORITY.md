# OULA — Governance and integration idempotency authority hardening

**Status:** developer implementation; production assurance remains a separate release gate.

## Covered boundaries (9 commands)
- Compliance policy evaluation, policy rule creation, approval requests and approval decisions (4).
- Integration partner registration/verification, contract creation and outbound request preparation (4).
- Settlement reference recording (1). A settlement reference is *not* proof that a bank transfer occurred.

Each controller first uses `WorkspacePurposeAuthorizer` to verify signed JWT actor,
workspace, purpose and required operation scope. The idempotency request hash is then
bound via `RequestFingerprint.scoped(access.workspaceId(), access.actorId(), access.purpose().name(), fingerprint)`
before the existing atomic database claim/mutation/receipt transaction.
No new dependency from Platform back to IAM and no database migration.

## Security behavior
- Same actor + same workspace/purpose/payload/key -> completed reply may be replayed.
- Different actor with valid workspace membership, same key/payload -> HTTP 409 conflict;
  stored response is not disclosed.
- Different purpose -> HTTP 409, even when an actor has both authorized purposes.
- Different request body -> HTTP 409 as before.
- All original scope and domain policy gates remain required.

## Proof and limits
JUnit/Postgres API integration tests exercise same-actor replay and cross-actor rejection
on compliance and partner registration. Exact-head Java and browser CI, CodeQL, and
Modulith dependency checks are required before merging.
External systems need their own durable deduplication and partner acknowledgment strategy.
This change does not authorize banking, government, property title, or production OIDC
integration and does not cover all OULA controllers.
