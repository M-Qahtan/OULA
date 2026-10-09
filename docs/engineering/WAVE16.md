# OULA Engineering Wave 16 — Rental Financial Truth & Occupancy Intelligence

## Objective

Transform canonical Wave 13 rent schedules and physical handover observations into
trustworthy, privacy-aware operational decision-support outputs, without inventing
bank confirmations, legal claims, or unobserved occupancy.

## Financial truth vocabulary

- CONTRACTUAL_DUE: periodic rent due under a recorded active/ended lease.
- RENT_RECEIPT (documentary): a human-verified document registered as evidence.
- REVERSAL: evidence-backed full reversal of one original receipt; historical
  original remains append-only.
- DOCUMENTARY_COVERED: evidence balance equals a contractual installment.
- OVERDUE_DOCUMENTARY_GAP: an installment date is past and documentary balance
  does not cover due. **Not** independently verified bank nonpayment.
- BANK_CONFIRMED: explicitly NOT available in this wave.

## Invariants

1. Never mutate or delete evidence entries; a full linked reversal is required.
2. One verified evidence ID may not be used twice.
3. A receipt amount must be positive and no greater than unallocated installment.
4. A reversal must target one original receipt in the same workspace, lease, installment.
5. Only one reversal of a receipt is allowed.
6. Workspace, lease, installment and currency must match; enforced by database FKs.
7. No credit balance, advance fund custody, real payment, payment initiation or legal
   collections action is implemented.
8. Occupancy denominator counts only units with actual dated handover observations;
   UNKNOWN is never auto-converted to vacant.
9. Financial as-of dates use Riyadh business day. No future financial observation.
10. All writes use IAM purpose/scope, idempotency, audit and Outbox.

## Engine outputs

- RentalFinancialSummary — total contractual, net documentary evidence, documentary
  gap due by date, overdue installments and detailed installment evidence statuses.
- PropertyOccupancyInsight — active unit count, known, recorded occupied, recorded
  vacancy, unknown, known-coverage ratio and occupied fraction of known units.

## Evidence & policy boundaries

- docs.evidence verification status VERIFIED is still an internal verification;
  it does not attest external bank settlement or government registry truth.
- Transactional source tables remain canonical; these read models are derived.
- Immutable entries are intended for downstream Reality Memory evaluation,
  but cannot automatically become Property Facts or financial postings.
- No direct implementation of an occupancy Vital dimension until a versioned
  policy/governance upgrade validates its thresholds.

## Exit gates

- [ ] Flyway V001-V018 applied on PostgreSQL/PostGIS
- [ ] No cross-module architecture regression
- [ ] Partial receipt, over-allocation, reversal, duplicate reversal tests
- [ ] DB append-only and workspace isolation tests
- [ ] UNKNOWN vs occupied vs vacancy denominator tests
- [ ] API scope, purpose and idempotency tests
- [ ] Full prior regression suite and CodeQL green
