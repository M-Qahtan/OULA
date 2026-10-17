# NEXUS Office Membership — Signed Browser/Java/PostgreSQL Proof

## Scope
The `/office-proof` page is **DEV_TEST_ONLY** and unavailable without an
explicit `NEXT_PUBLIC_OULA_DEV_AUTH=true` and a backend URL. It does NOT
represent a registered Saudi real-estate business or a legally authorized
broker. The page calls the actual `/v1/offices/members` and institutional
invitation APIs, not mock arrays.

## Actors
- `office-admin`: synthetic ADMIN seeded **only under** Spring profile
  `dev-auth & !prod` into fictional workspace ending in `0005`.
  Receives only `oula.office.members.manage` scope.
- `office-invitee`: a second JWT-signed actor in the *same* fictional
  workspace; deliberately not pre-enrolled. Receives only
  `oula.office.membership.accept`.

The browser holds JWT strings **in React state only**, never localStorage
or sessionStorage. Admin may invite a specific actor+issuer+subject. Invitee
accepts with their own token; until then no access to institution data. Admin
may revoke afterwards. Office API always revalidates JWT, actor and actual
database membership, and rechecks grant ownership in transactional services.

## Browser verification
End-to-end test tests two signed fixture sessions, compares true PostgreSQL
roster and invitation counts, ensures no membership until explicit accept,
then verifies revocation and page-refresh session cleanup. Separate test
ensures invitee and foreign workspace cannot list office members.

**One-shot fixture caution:** A revoked actor is not automatically re-enabled
on restart. The complete invite->accept->revoke browser test requires a clean
dev-test DB. This is intentional, to avoid building an unsafe silent join path.

## Production gaps
External trusted IdP / organization onboarding, legal authority checks,
real notification delivery, real account management, accessibility audit,
recurring membership recovery / offboarding workflows, pagination and a
production session-handling strategy remain outstanding. No financial or
government actions are performed here.
