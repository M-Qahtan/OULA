# OULA Engineering Wave 17 — Rental Risk & Lifecycle Intelligence

## Mission

Translate canonical tenancy, documentary rent coverage and physical handover
observations into explainable human-review priorities. Preserve separation of
advice, authority, evidence and real-world actions.

## Data flow

Property / Unit / Lease / Contractual Installments / Verified Documentary Receipts
+ Renewal Decisions + Recorded Handovers
→ deterministic, versioned rental lifecycle advisory
→ role/purpose-scoped UI recommendations for Property Guardian managers.

Wave 13 already materializes a Guardian renewal-review obligation when a signed
lease is activated. Wave 17 complements that system with **read-only advice**;
it neither duplicates persistent obligations nor silently creates Action Items.

## Advisory rules v1

- UNKNOWN occupancy → collect human-verified handover evidence (not vacancy).
- Recorded check-out → review present unit status before re-letting.
- Lease end within 60 days → request contractual renewal decision review;
  <=14 days raises follow-up priority; recorded term in the past requires an
  authorized legal/operational status check.
- Recorded RENEWAL_ACCEPTED → verify new signed instrument; it does not
  automatically extend the old contract.
- No contractual rent schedule on an ACTIVE lease → verify source data.
- Documentary rent gap → review documentary coverage; two or more overdue
  installment lines increase administrative review priority.
- Document gap does NOT prove unpaid rent, legal arrears or failed bank transfer.

## Authority and architecture

- Existing `advisory` bounded context reuses public `tenancy` domain services.
- No migrations, secondary fact storage, automation, fund movements or LLM inference.
- GET-only authenticated API, PROPERTY_MANAGEMENT purpose and dedicated
  `oula.property.rental-advisory.read` scope.
- Assessment output includes rules version, UTC generation time, local Riyadh
  as-of date, IDs, evidence metrics, coverage limitations and HUMAN_REVIEW_REQUIRED.
- Fail closed on observed active-unit count mismatch; first-wave bounded
  request size 250 active units. This N+1 read design must be replaced with
  efficient batch projections before large multi-building portfolios.
- Read-only transaction; no write to obligations, cases, documents, leases,
  rent receipts, evidence, audit or outbox.
- Only human authorities can approve any operational, legal or financial steps.

## Quality gates

- [ ] Unit rules: renewal intent, documentary gap, UNKNOWN, no units and racing data.
- [ ] MockMvc: scope/purpose/workspace denial and zero side effects.
- [ ] Existing migrations through V018 and all regressions.
- [ ] Spring Modulith verifies updated advisor→tenancy boundary.
- [ ] CI and CodeQL green before squash merge.
