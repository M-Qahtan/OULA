# OULA — Contextual Owner Intake, Capability-Gated Navigation

**Implementation status:** integrated development capability projection; not production owner verification.

## Why it matters
OULA's HABITAT family must present property management actions only when the authenticated
workspace and permitted purpose have their corresponding existing backend scopes.
The five-family visual navigation is not itself an authorization system.

## Signed discovery
Request `GET /v1/experience/manifest` with an authenticated bearer, workspace and
`X-OULA-Purpose: PROPERTY_MANAGEMENT`. The Java server first requires `oula.experience.read`,
checks the actor JWT membership and purpose, and projects:

| Capability | Route | Required pre-existing scope | Mode |
|---|---|---|---|
| PROPERTY_INTAKE | /v1/properties | oula.property.write | COMMAND |
| PRIVATE_LISTING_DRAFT | /v1/properties/{propertyId}/listings | oula.listing.write | COMMAND |
| PRIVATE_LISTING_READ | /v1/listings | oula.listing.read | QUERY |

The Next.js gateway renders a link to `/owner-intake-proof` only when both
`PROPERTY_INTAKE` and `PRIVATE_LISTING_DRAFT` appear with COMMAND mode in the
HABITAT manifest. A client-side link is **not a security boundary**: every underlying
property/listing API independently enforces JWT workspace, purpose and scope.

## Test evidence required
- Java unit tests: partial scopes project only the corresponding action, and
  a buyer-purpose token with the same business scopes cannot obtain HABITAT commands.
- Browser + backend tests: signed fictional property manager gets all three capabilities;
  buyer does not see intake controls and no owner-intake navigation is projected.
- Existing owner-intake end-to-end: creates fictional DECLARED property, private
  DRAFT listing and reads it back from PostgreSQL/PostGIS.

## Honest boundaries
Fixture data is `DEV_TEST_ONLY`. There is no claim of owner or property-title
verification, public publication, licensed brokerage, bank/payment or government integration.
Production identity, permissions, policy, consent, abuse controls, audit and approved data
sources remain separate release gates.
