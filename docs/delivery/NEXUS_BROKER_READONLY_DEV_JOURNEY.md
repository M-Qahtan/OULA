# NEXUS — Signed Read-Only Broker Development Experience

**Status:** DEV_TEST_ONLY. Not licensed brokerage, deal execution, or an external integration.

## Target
Allow OULA to demonstrate a **third distinct user-family workspace** beyond buyer
PULSE and property manager HABITAT without accidentally issuing transaction powers
merely because a user selected the NEXUS interface.

## Development fixture
The broker alias is `broker-readonly`: fictional actor UUID ending `0004`, isolated
workspace UUID ending `0004`, and only these claims:
- purpose: `TRANSACTION_EXECUTION`
- scopes: `oula.experience.read`, `oula.transaction.read`

The fixture is provisioned **only** when `dev-auth` is enabled without `prod`.
It is not a verified broker identity, registered brokerage license, client authorization
or property mandate. The fixture's workspace does not contain real third-party deals.

## Server capability projection
`GET /v1/experience/manifest` must authenticate bearer, workspace, purpose
and `oula.experience.read`. The server returns the NEXUS lens with two queries:
`DEAL_READ` and `DEAL_ROOM_READ` (`oula.transaction.read`).
No `DEAL_ADVANCE` because `oula.transaction.advance` was never granted.
The deal-room query is an existing read-only Java endpoint; access to any *actual*
transaction is independently protected by its workspace check.

## UI and negative gates
The development Gateway allows switching among signed buyer, owner and broker.
The broker's authenticated link goes to `/broker-proof` (DEV-only), where a
fictional DRAFT deal is read from the actual Java/PostgreSQL transaction subsystem.
The general `/gateway/nexus` remains an educational concept, not a live
brokerage console. Signed browser/JWT integration tests assert:
- No buyer or owner capabilities leak into the broker lens.
- No transaction mutation is authorized (HTTP 403).
- A broker can retrieve only its own seeded DRAFT Deal Room, whose
  `availableCommands` is empty; the browser forgets the development token
  when the page reloads.
- A broker's JWT cannot access another workspace's manifest (HTTP 403).
- Broker read-only capabilities and the preview disclaimer are visible bilingually.

## Required to become a real brokerage product
Production OIDC login, verified organization/workspace membership, licensed brokerage
status where required, representative authorization, client-specific grants, deal
visibility model, audit, consent, RLS, privacy/data controls and approved provider
integrations. Nothing in this demo provides those assurances.
