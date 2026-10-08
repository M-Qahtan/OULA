# OULA Engineering Wave 08 — Service Graph & Settlement Boundary

## Production loop

Guardian Action → Work Order → Verified Provider → Qualified Capability → Quote → Human Selection → Approved Budget + Assignment → Execution → Evidence-backed Verification → Provider Outcome → External Settlement Reference.

## Delivered

- workspace-scoped operational service-provider profiles;
- evidence-backed provider verification state;
- provider capability registry;
- comparable quotes bound to one Work Order;
- provider eligibility and category qualification gates;
- human quote selection that authorizes the selected amount and provider assignment;
- automatic rejection of non-selected submitted quotes;
- observed provider outcome after verified completion;
- explicit quoted-vs-actual cost variance;
- provider-neutral external settlement references;
- settlement evidence requirement for SETTLED state;
- cumulative settlement guard against verified actual cost;
- Audit + Outbox coverage;
- dedicated OAuth scopes under PROPERTY_MANAGEMENT;
- PostgreSQL constraints and integration coverage.

## Invariants

1. Provider operational profile != canonical legal organization identity.
2. Provider registration != provider verification.
3. Capability declaration != verified completion quality.
4. Quote != approval.
5. Quote selection is a human authority event.
6. Selected quote may authorize budget and assignment, but never initiates external work by itself.
7. Work Order != payment record.
8. Settlement Reference != fund movement.
9. SETTLED status requires evidence.
10. Cumulative settled amount cannot exceed verified Work Order actual cost.
11. Provider Outcome is observed history, not an inferred reputation score.
12. Future external marketplace/payment adapters remain provider-neutral integrations.

## Exit gate

Wave 08 may merge only after:
- Flyway V001 → V013 on PostgreSQL/PostGIS;
- all Golden Path, Decision, Reality Science, Guardian and Work Order regressions;
- verified-provider eligibility test;
- quote selection / budget / assignment transaction test;
- provider outcome test;
- settlement overrun guard test;
- API purpose/scope test;
- Spring Modulith verification;
- CodeQL green.
