# OULA Institutional Membership Trust Boundary — Phase 1

**Implemented:** `V025` and `InstitutionalMembershipRegistry` in the IAM bounded context.

## Contract
- JWT signature/issuer validation occurs in Spring Security. API requires a JWT subject,
  OULA actor UUID, claimed workspace, purpose, and exact operation scope.
- PERSONAL workspaces preserve the prior access flow (JWT claims + purpose/scope
  + transaction-specific grants); disabled or unknown workspaces are rejected.
- For every **non-PERSONAL workspace**, the server additionally requires an
  active database roster entry matching ALL of workspace ID, actor UUID, JWT
  issuer URL and JWT subject. A member's role label does not grant scopes.
- Deal-owner invitations in nonpersonal workspaces can target only active
  roster actors. A recipient must also explicitly accept the private deal
  invitation with their own token. Revoking office membership stops API access
  even if an existing JWT and accepted deal grant remain valid.
- The roster is not managed by an unsafe self-registration API. Only a separate,
  trusted operator/organization onboarding pipeline may populate production
  `TRUSTED_BOOTSTRAP` entries. `DEV_FIXTURE` is isolated to local simulation.

## Scope and limitations
This is an **internal membership allowlist**, not external proof that an
organization is legally registered, a broker licensed, or an individual an
authorized representative. Real production onboarding requires IdP connection,
assurance levels, authenticated organization administration with audit,
independent representation/license verification where applicable, join/leave
flows, recovery, and revocation event delivery. Schema stores the IdP issuer and
subject to prevent identically named subjects at different providers from being
treated as the same person.

## Security and operational tests
Spring JWT + PostgreSQL integration tests exercise:
missing roster, wrong subject/issuer, cross-actor access, active membership,
deal recipient not listed, invitation/accept read, and instant membership
revocation blocking deal-room read even with the signed token. Existing
personal-user and fictional broker browser journeys must continue to pass.

**Deployment:** V025 introduces a new table with no implicit auto-enrollment
for existing institutional workspaces. The effect is fail-closed access until
a trusted import of institution member identities has occurred; preflight a
copy of the production database and approved IdP binding before applying.
