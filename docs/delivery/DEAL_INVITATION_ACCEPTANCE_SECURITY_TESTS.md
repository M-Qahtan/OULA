# OULA — Two-step deal-read invitation protection

## The risk
A transaction originator previously could grant immediate deal visibility to an arbitrary actor UUID. The intended private deal-room contract now requires that the recipient explicitly accept the invitation under their OWN authenticated JWT and matching workspace.

## Protocol
1. Originator with `oula.transaction.grant.manage` posts a time-limited VIEWER invitation. The result has `active=true`, `accepted=false`; **no read access is granted yet**.
2. Invited actor with `oula.transaction.read` calls `POST /v1/transactions/{id}/read-grants/accept` using a unique Idempotency-Key. An atomic update sets `accepted_at` and appends an audit outbox event.
3. Read access is then permitted only while `active=true`, `accepted_at IS NOT NULL`, workspace and actor match, and `expires_at > now()`. A VIEWER never gains transaction mutation authority.
4. Originator may revoke an invitation before or after acceptance. Revoked/expired invitations cannot replay a cached accept as successful while authorization is no longer active.
5. Re-inviting an existing VIEWER resets acceptance and requires new consent.

## Migration and evidence
V024 sets accepted_at only for ORIGINATOR records; existing viewers default to pending, denying historical viewer read access until they accept. This is an intentional access-tightening migration; communicate the change and test migration on a copy of existing data before production deployment.

JUnit/Postgres API tests cover denial before acceptance, wrong-actor acceptance, missing scope, successful acceptance, same-key replay, viewer command denial, owner revocation, blocked revoked replay, re-invitation and expired viewer denial. DEV_TEST_ONLY broker fixture auto-accepts only for the fictional sample actor, never for actual users.

## Not yet production authority
Neither actor UUID nor workspace alone proves organizational affiliation, a licensed brokerage mandate, legal property authority or recipient identity in a real external IdP. Implement real OIDC tenant provisioning, verified institutional memberships, consent UX, delivery/notification of invitations, privacy and audit retention before commercial launch.
