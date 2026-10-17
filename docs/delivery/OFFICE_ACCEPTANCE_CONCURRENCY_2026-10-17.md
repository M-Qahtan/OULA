# Institutional invitation acceptance: concurrency evidence

Baseline: `main@b0ec6a193ee6d85507696b980a266c024f3c6e33` (#109).
Tracking: #111; implementation and evidence: #112.

The existing institutional lifecycle and signed DEV-only NEXUS console were
already merged through #105, #107, #108 and #109. This change closes admission
races in that implementation; it does not duplicate migrations or onboarding.

## Reproduction

Test-only commit `b2b5bb82c76a0055bdbac408cc7c4bd4f7d56985` ran against real
PostGIS 16/3.4 and Java 21 in [CI 39056785912](https://github.com/M-Qahtan/OULA/actions/runs/39056785912).
All three new tests failed because admission succeeded when it should be denied;
the other 128 tests passed (131 total, 3 failures, zero errors/skips).

1. Cancellation transaction locks the invitation. Acceptance is observed waiting
   through `pg_blocking_pids`, then cancellation commits its separate ledger row.
   The old acceptance SELECT retains its pre-wait cancellation snapshot and admits.
2. An acceptance transaction starts before invitation expiry, waits beyond expiry,
   then calls acceptance. PostgreSQL `now()` still returns transaction start.
3. The office becomes inactive after the pre-idempotency guard. The old mutation
   does not recheck workspace status and admits the recipient.

## Correction and contract

Admission holds a shared active-workspace row lock until transaction completion,
locks the invitation, then repeats the recipient/cancellation/expiry guard in a
fresh READ COMMITTED statement. Pending expiry uses `clock_timestamp()`.
Cancellation and admission serialize on the same invitation row. If admission
wins, cancellation of that accepted invitation fails and normal membership
revocation is required; if cancellation wins, admission fails without membership,
acceptance audit or outbox writes. No existing invitation receipt creates a new
membership. Existing API identity binding, replay guards and role limits remain.

NEXUS keeps UUIDs LTR within the RTL roster and wraps long identifiers. A signed
browser test checks horizontal overflow at 390x844, 1024x768 and 1920x1080.

## Verification and release boundary

Final-head CI, frontend, browser integration and CodeQL results must be inspected
on #112 after the last code edit. An independent reviewer must approve before
merge; green automation alone is insufficient. No production identity provider,
broker licensing verification, external invitation delivery, government integration,
field acceptance or deployment is claimed. OULA remains independent of ROS.
Rollback is a code revert: no schema migration is introduced by this change.
