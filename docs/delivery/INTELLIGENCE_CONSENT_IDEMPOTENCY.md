# OULA Evidence, Decision, Consent and Reality Review Replay Isolation

## Covered commands (14)
- DecisionIntelligence: record evidence, record assumption, generate recommendation,
  record human decision and record observed outcome (5).
- PropertyState: record a declared state snapshot (1).
- RealityScience: record a reviewed reality gap (1).
- Consent: append an explicit grant or revocation event (1).
- ServiceGraph: provider registration/verification/capabilities, quote submission/
  selection and provider outcome recording (6).

All original authorizer checks and domain safety rules remain in force.
Receipt fingerprints now bind to the verified JWT actor ID, workspace and purpose
through `RequestFingerprint.scoped`. No new dependency from Platform to IAM.
Consent's original hash already included actor and purpose; using the standardized
versioned scoped hash makes the boundary uniform across controller families.

The source of evidence or statements remains explicit (verified, declared, inferred
or simulated). These changes do not validate the truth of evidence and do not
authorize autonomous decisions, government integrations or real banking.

## Evidence
- Existing real PostgreSQL integration tests for decision outcome and intelligence
  history still run.
- New signed actor-replay tests: the original actor can replay an evidence response,
  while a separate service-provider test rejects an unauthorized replay attempt;
  in both cases,
  a second authorized actor in the same workspace receives HTTP 409.
- Exact-head Java/PostGIS, Spring Modulith, CodeQL and browser integration gates
  are required before merge.

## Migration
Older pre-hardening idempotency receipts produce a conflict on reuse of the same key
rather than being exposed across identities. Evaluate production retention and
migration strategy before applying this release to non-fictional data.
