# OULA Engineering Wave 10 — Policy Enforcement Integration

## Purpose

Wave 09 created the Trust, Compliance & Autonomous Approval Kernel. Wave 10 connects that kernel to material operational mutations inside domain services so an internal caller cannot bypass policy merely by skipping the HTTP layer.

## Enforced operations

Policy enforcement now precedes:
- WORK_ORDER_APPROVE;
- WORK_ORDER_ASSIGN;
- WORK_ORDER_START;
- SETTLEMENT_REFERENCE_RECORD.

Each policy decision is persisted before the mutation.

## Human and autonomous authority

Existing human PROPERTY_MANAGEMENT flows remain compatible, but they are now evaluated through the explicit human baseline policy.

AUTONOMOUS_EXECUTION is allowed to reach only the policy-gated work-order approve/assign/start seams and settlement recording seam. The agent must supply a valid approval identifier whenever the active policy requires approval.

The following remain human-only in Wave 10:
- Work Order creation;
- completion submission;
- completion verification;
- listing/management read surfaces that are not needed by the autonomous execution seam.

Completion verification intentionally remains human-only so an agent cannot attest to its own execution outcome.

## Context integrity

Policy-aware API calls accept:
- X-OULA-Jurisdiction;
- X-OULA-Approval-ID.

Jurisdiction defaults to UNSPECIFIED for backward-compatible human flows. OULA does not infer a jurisdiction merely to satisfy policy.

Idempotency fingerprints include the jurisdiction and approval identifier.

Approval remains bound by the Wave 09 kernel to requester, purpose, action, resource, jurisdiction, amount and currency. An approval for WORK_ORDER_APPROVE cannot authorize WORK_ORDER_ASSIGN.

## Settlement boundary

Autonomous settlement recording is permitted only after:
1. Work Order is verified COMPLETED;
2. provider assignment exists;
3. actual cost is verified;
4. currency matches;
5. cumulative settled amount does not exceed actual cost;
6. policy allows the exact operation;
7. SETTLED state includes evidence.

The Settlement Reference still does not move money.

## Exit gate

Wave 10 may merge only after:
- all existing regressions remain green;
- human legacy flows remain compatible;
- autonomous transition without approval is blocked before mutation;
- exact action-bound approval permits the intended transition;
- approval reuse for another action is rejected;
- completion verification remains human-only;
- autonomous settlement is blocked until exact approval;
- API exposes policy precondition details;
- Spring Modulith verification;
- CodeQL green.
