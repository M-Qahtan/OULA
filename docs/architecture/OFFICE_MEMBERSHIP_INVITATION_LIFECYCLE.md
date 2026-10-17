# OULA Office Membership Invitation & Offboarding — Phase 1

## Trust boundary
Only an **active institution ADMIN originally provisioned through a trusted administrative path**
can invite BROKER or STAFF. A JWT OAuth scope is NOT sufficient without a
matching active issuer+subject+actor membership. Office management routes are
`POST /v1/offices/members/invitations`,
`POST /v1/offices/members/invitations/{invitationId}/accept`, and
`POST /v1/offices/members/{actorId}/revoke`.

Invitations are addressed to a fixed `actorId` **plus** IdP `issuer` and `subject`
with a maximum validity of 14 days. Recipient acceptance requires a valid
bearer JWT, matching `actor_id`, issuer, subject, the `TRANSACTION_EXECUTION`
purpose and `oula.office.membership.accept` scope. Crucially, **the recipient
does not need an existing workspace-membership claim before joining**.
They must know the invitation ID, delivered by a separately authorized
transport; this API does not email or publish it. A guessed invitation ID
does not disclose identity or details.

The accepting transaction records the member as
`ADMIN_INVITATION`, grants only `BROKER` or `STAFF`, and creates
immutable audit + outbox evidence. No self-service `ADMIN` registration,
role elevation, change of IdP identity, or activation of revoked membership.

## Removal
Existing institutional ADMIN with `oula.office.members.manage` may revoke
non-ADMIN active members. Revocation and transaction-specific active VIEWER
grant invalidation are coordinated in the **same database transaction** with
a single idempotency receipt. Revoked signed JWTs immediately stop accessing
the institution; previously accepted invite acceptance cannot replay after
revocation. Historical ORIGINATOR deal permissions remain inaccessible
while membership is revoked and must be explicitly reconciled by a trusted
administrator before any future institution re-provisioning.

## Institutional membership directory and invitation inbox

Read-only discovery routes are backed by the same PostgreSQL identity boundary:

- `GET /v1/offices/members` lists up to 100 ACTIVE office actors and their role,
  restricted to active, signed, issuer+subject-bound institutional ADMIN with
  `oula.office.members.manage` and `TRANSACTION_EXECUTION` purpose.
- `GET /v1/offices/members/invitations` lists up to 100 actionable pending
  invitations in that office; cancelled, expired and accepted invitations are
  excluded. No identities or private deals from other workspaces are returned.
- `GET /v1/offices/members/invitations/mine` is the only discovery path for
  invitees BEFORE membership. It verifies a signed `actor_id` + issuer +
  subject and the `oula.office.membership.accept` scope, even when the JWT
  has no workspace membership claim. It returns only that actor's unexpired
  uncancelled invitation matching the exact supplied workspace ID.
- All three routes respond `Cache-Control: no-store` and return no OAuth
  secret, property content, or deal details.
- The first-100 limit is deliberate; full pagination and institution-verified
  onboarding / notification UX remain follow-up work.

## Safety and scope
- No real brokerage licence, property authority or legal representation
  is verified by these APIs. Admin bootstrap is separately controlled.
- Invitation IDs and invitee binding are UUID + issuer/subject; no untrusted
  request header grants authority. Invite acceptance cannot be made on
  behalf of the recipient.
- Unique workspace/actor invitations are **one-time** in this milestone:
  expired invitations require trusted manual review rather than a silent
  reissue. A revoked member cannot self-join again.
- Idempotency-Key, X-OULA-Workspace-ID and X-OULA-Purpose headers are mandatory.
  Office manager operations require verified active ADMIN and
  `oula.office.members.manage`; invites and revoke use
  `TRANSACTION_EXECUTION` purpose only.
- Production delivery/notification of an invitation, IdP provisioning,
  institution verification, member directory UI, recovery, elevated assurance,
  regulatory broker licensing and audit retention remain separate work.
- PostgreSQL JUnit tests exercise role policy, malicious OAuth scope,
  wrong issuer/subject/actor, absent prejoin workspace claim, replay, expiry,
  consent + deal read preservation and immediate atomic offboarding.
  Browser CI rechecks existing fictional broker journey.
