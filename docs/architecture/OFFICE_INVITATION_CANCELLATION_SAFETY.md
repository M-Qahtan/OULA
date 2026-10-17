# OULA — Pending Office Invitation Cancellation Safety Gate

## Why the extension exists
V026 had an identity-bound office invitation that expired after 14 days and
could be accepted by its intended recipient, but no administrator cancellation
route. That left a pending invitation valid for days after the sender changed
its mind or discovered an affiliation risk. This is particularly sensitive
for broker, office and institutional access.

## V027 read-only evidence and permanent cancellation
A distinct `iam.institutional_invitation_cancellation` table preserves the
immutable original invitation and appends the administrator actor and timestamp.
The original invitation remains PENDING in its own table for historical
evidence; the *effective* status is CANCELLED whenever a cancellation row exists.
This is deliberate and avoids rewriting previously recorded facts.

`POST /v1/offices/members/invitations/{invitationId}/cancel`:
requires TRANSACTION_EXECUTION, `oula.office.members.manage`, a valid JWT
workspace claim, and ACTIVE database ADMIN membership. Only PENDING invitations
in that exact institutional workspace are eligible. The operation records an
audit row and an outbox event `iam.office.invitation.cancelled.v1` in the
same transaction. Same-key idempotency replay is permitted to the same
authorized ADMIN, but a second key cannot create a second cancellation.

Acceptance of an invitation checks the cancellation ledger BOTH before
idempotency replay and inside the transaction's SELECT FOR UPDATE. The
cancellation command also locks the invitation row, so a race resolves
as either acceptance first (cancellation rejected) or cancellation first
(acceptance rejected); never both.

This is NOT a legal broker mandate, professional licensing verification,
bank authorization, title verification, or institutional IdP rollout.
Tests must pass PostgreSQL/JWT CI, browser integration, and CodeQL for the
same commit before integration.
