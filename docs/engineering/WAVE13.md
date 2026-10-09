# OULA Engineering Wave 13 — Lease, Occupancy & Tenancy Truth

## Mission

Establish canonical lifecycle distinctions across Property, Unit, Lease, physical
possession, recurring contractual dues and renewal decisions.

## Explicit state transitions

- Unit: ACTIVE registration distinct from Property Asset.
- Lease: DRAFT -> SIGNED -> ACTIVE -> ENDED; only DRAFT can be CANCELLED.
- Signing requires verified LEASE_CONTRACT evidence in the same workspace.
- Activation requires a signed, in-term lease and an active Property Guardian
  management enrollment; activation materializes contractual installments and
  one Guardian renewal-review obligation (60 days before expiry).
- Physical possession begins only on verified HANDOVER_RECORD.
- Physical possession ends only on verified HANDOVER_RETURN.
- Ending an active lease requires completed check-out plus verified
  LEASE_TERMINATION evidence; it does not assert rent collection.
- Renewal decisions are append-only. RENEWAL_ACCEPTED expresses intention
  and does not extend the existing contractual period.
- Overlapping signed/active lease periods are prohibited at database level.
- Every mutation is workspace-bound and carries actor, purpose, audit,
  outbox and HTTP idempotency semantics.

## Canonical truth boundaries

Lease != occupancy != government registry.
Rent due != rent paid != money movement.
Landlord/tenant Party references != externally verified legal identities.
Intent to renew != executed renewal contract.
UNKNOWN physical state is not declared VACANT without a handover return event.

## Coverage

Unit, Lease, RentInstallment, Occupancy, RenewalDecision, Guardian review,
VerifiedDocumentEvidenceService, OIDC scopes, OpenAPI and AsyncAPI contracts,
Flyway V017, Spring Modulith module boundaries, service/API integration tests.

## Explicit deferred work

Saudi Ejar verification, legal signatures, authority verification, utility
handover, refundable deposits, receipts, rent settlement, arrears, multi-tenant
occupancy and all global locality/timezone policies are separate follow-up
capabilities; no mocks masquerade as real integrations.

## Exit gate

- [ ] Flyway V001-V017, including exclusion constraints
- [ ] Spring Modulith boundary check
- [ ] Golden Path and all previous Wave regressions
- [ ] Leasing lifecycle and occupancy truth test
- [ ] Evidence-type and cross-workspace denial tests
- [ ] API idempotency and scope tests
- [ ] CodeQL green
