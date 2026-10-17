# OULA Office Membership Onboarding — Wave 26

## Human-first office identity and authorization
The operating invariant is: a signed JWT with a workspace ID and a generous
scope cannot unilaterally enroll a user in a company or real-estate office.

An established institutional ADMIN must already have a trusted provisioned
membership in `iam.institutional_membership` bound to the exact workspace,
actor_id, OIDC issuer and subject. Only that ADMIN with
`oula.office.members.manage` can issue staff/broker invitations (valid for
at most 30 days). No caller can invite an ADMIN role. This remains separate
from licensed broker mandates or property/transaction rights.

## Exact API and state model
- `POST /v1/offices/members/invitations`: ADMIN creates a PENDING invitation
  to known recipient actor_id and subject, restricted to the ADMIN's issuer.
- `POST /v1/offices/members/invitations/accept`: the named actor's OWN signed
  issuer+subject identity and `oula.office.members.accept` scope accept.
  This is the ONLY endpoint where workspace JWT claim checks are allowed
  before active membership; an invitation must exist, be PENDING/unexpired,
  match the identity, and carry a current version.
- `GET /v1/offices/members`: ACTIVE institutional ADMIN only, plus
  `oula.office.members.read` scope.
- `POST /v1/offices/members/{actorId}/revoke`: ACTIVE institutional ADMIN
  only; immediately disables BROKER/STAFF roster membership and cancels any
  still-active invitation. Neither self-removal nor ADMIN removal is allowed.

All mutations are serialized under workspace row locks, use actor/purpose-
scoped idempotency keys, append outbox events, and write actor audit records.
Invitation renewals increment a monotonic version, so a previous successful
accept receipt cannot reactivate a new invitation. No email, phone or
personal identifier is a stand-in for the signed IdP subject.

## Operational notes
V026 creates `iam.institutional_member_invitation` and extends the
membership provenance enumeration to `INVITATION_ACCEPTED`, retaining
`TRUSTED_BOOTSTRAP` for externally provisioned memberships and
`DEV_FIXTURE` for nonproduction fixtures only. The migration does not
auto-enroll accounts. Institutional bootstrap and legal authorization remain
separate controlled processes.

Acceptance creates backend membership, **not** a new IdP token, nor a scope
upgrade. The identity provider must issue the appropriate workspace and
purpose/scope claims for subsequent requests. This change does not prove
professional licensing, authorization to represent sellers, official title
or brokerage mandates.

## Regression criteria
PostgreSQL/JWT MockMvc tests must demonstrate non-admin denial even with
management scope, cannot self-register, wrong issuer/subject/actor denial,
pending ≠ active, successful recipient acceptance, read-only directory
administration, blocked ADMIN revocation, revocation invalidating a still-valid
JWT, blocked stale receipt after re-invite, expired invitation rejection,
personal workspace isolation, and audited/outbox actions. CI, browser integration,
CodeQL must all succeed on the identical PR head before main merge.
