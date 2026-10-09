# OULA Engineering Wave 18 — Human Decision & Outcome Feedback Loop

## Mission

Close the *human feedback* loop over evidence-grounded Rental Advisory without
confusing recommendation acceptance, real-world execution, verified outcome or
causal attribution. Extend OULA's institutional memory through a permissioned,
append-only human-review trail, not automated learning.

## Four distinct truths

1. **Advisory Capture** — freezes the generated recommendation, source metrics,
   rule version, identity references, generation timestamp and fingerprint.
   Capture verifies current source action, unit, optional lease and rule version.
2. **Human Disposition** — append-only ACCEPT_FOR_REVIEW, DEFER or DECLINE with
   actor, workspace, recorded time and rationale. An acceptance gives no
   approval to spend, perform work, send financial demands or act legally.
3. **Outcome Observation** — requires the most recent human decision to be
   ACCEPT_FOR_REVIEW and independent workspace-matched VERIFIED evidence of
   type ADVISORY_OUTCOME. Observation categories: IMPROVEMENT_OBSERVED,
   NO_CHANGE_OBSERVED, DETERIORATION_OBSERVED or INCONCLUSIVE.
4. **Aggregate Feedback Coverage** — counts captured cases, cases with human
   decisions, cases with documentary outcome observations and recorded labels.
   None is a causal success rate or validated algorithmic accuracy.

## Platform invariants

- Immutable PostgreSQL tables for frozen review cases and sequenced events.
- Case lock serializes decision and outcome ordering.
- No cross-workspace case or document access.
- HTTP POST operations carry Idempotency-Key and independent OAuth scope.
- All writes use Audit + Transactional Outbox + Actor + Purpose + Correlation.
- Public endpoints require PROPERTY_MANAGEMENT.
- Review history does not automatically modify Tenancy, Property, Vitals,
  Guardian, Transaction, Fact or Reality Memory canonical truth.
- No automated training, policy update, legal action, payment or notification.
- The pilot focuses on rental advice; broad operational-advice feedback is a
  subsequent composable extension once review governance is verified.

## Exit gates

- [ ] Flyway V001–V019 / PostgreSQL trigger immutability
- [ ] Source-matched capture and source metric freeze
- [ ] Decision / outcome sequence and evidence check
- [ ] Cross-workspace access and evidence denial
- [ ] Idempotency, OAuth purpose / scope, no-store reads
- [ ] Feedback summary counts are transparent and explicitly non-causal
- [ ] Modulith architecture, full regression tests, CodeQL
