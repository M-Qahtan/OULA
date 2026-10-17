# Material decision evidence eligibility

Tracking #115. Baseline main@5f6bc8c19e4e053f9b97fe3e2bfa3be2543fe3e2 after #114.

## Defect and correction

The former availability guard allowed UNVERIFIED/arbitrary status strings, ignored valid_from and used transaction-start now(). Human decision recording did not revalidate linked evidence. Add a separate material decision guard at recommendation generation and human decision recording. Keep documentary outcomes and reality review policy distinct.

The new guard requires at least one linked item, same workspace, status exactly VERIFIED, captured_at and valid_from no later than wall clock, and valid_until absent or strictly later than wall clock. Missing time fields, hash, source type, or both source identity/reference fail closed. Unknown/rejected/unverified/expired/future/foreign/missing records are ineligible. Registration rejects validUntil <= capturedAt.

Lock evidence rows FOR SHARE in sorted distinct UUID order inside the existing command transaction, then recheck eligibility in fresh statements after all locks are acquired. This serializes source status/metadata updates and avoids a stale pre-wait expiry snapshot. Eligibility is checked at admission, not promised forever or exactly at transaction commit. A later command revalidates the persisted links.

## Tests and semantics

PostgreSQL tests cover status allowlisting, mixed/foreign/missing sets, future/expired dates, long-transaction wall-clock expiry and undocumented sources. API tests deny recommendation generation from UNVERIFIED evidence, and deny decisions when linked evidence becomes REJECTED/EXPIRED/UNVERIFIED, expires by timestamp or loses all links. Denials assert no decision/recommendation, audit or outbox side effects. A documentary availability regression retains UNVERIFIED observation handling without upgrading it to FACT.

FACT/INFERENCE/RECOMMENDATION authority remains separate. This is eligibility/freshness validation, NOT independent verification of source authority: the authorized registration API still accepts caller-supplied VERIFIED. A trusted verifier/attestation policy is a separate necessary production gate. The legacy in-process IntelligenceKernel is not upgraded into an evidence-attested operational API by this change. No governmental/provider verification or production readiness is claimed.

Required before merge: four exact-head gates and independent security review. GitHub inspection found no configured main branch protection/rules; merges use expected head SHA after manual gate inspection, without admin bypass. Rollback: revert code; no migration added. No ROS, deployment, cloud spend or real external connection.

Additional verification: committed two-connection tests observe actual blocking
via pg_blocking_pids, then cover both unchanged-tuple expiry during the wait and
a concurrent VERIFIED-to-REJECTED update. Futures and cleanup have bounded waits.
Registration equality/reversed intervals and missing/future capture metadata are
also covered. API denial tests run in test transactions. A separate test without an outer
test transaction invokes the real idempotency service with an ineligible
evidence guard, then verifies committed-state absence of the claim, audit and
outbox rows after rollback. Concurrency tests use committed setup and separate connections.
