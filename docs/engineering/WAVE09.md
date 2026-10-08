# OULA Engineering Wave 09 — Trust, Compliance & Autonomous Approval Kernel

## Control loop

Actor / Agent → Purpose + Workspace + Scope → Policy Request → Deterministic Rule Match → ALLOW | DENY | REQUIRE_VERIFICATION | REQUIRE_DOCUMENT | REQUIRE_APPROVAL | ESCALATE → independent human approval when required → exact-context re-evaluation.

## Delivered

- deterministic workspace/system policy store;
- fail-closed decision when no policy matches;
- explicit jurisdiction field;
- amount/currency financial limits;
- evidence and verification prerequisites;
- workspace rules override system baselines by specificity;
- AUTONOMOUS_EXECUTION purpose;
- system baseline: autonomous execution requires human approval;
- 24-hour approval request TTL;
- Four-Eyes rule: requester cannot approve/reject the same request;
- approval binding to actor, purpose, action, resource, jurisdiction, amount, currency and policy rule;
- immutable policy decision records;
- Audit + Outbox coverage for policy creation, evaluation and approval decisions;
- secured policy/evaluation/approval API;
- V014 Flyway migration and integration tests.

## Security invariants

1. Policy rules are deterministic data, not executable scripts and not LLM prompts.
2. Match syntax is exact value or * only.
3. No matching policy means DENY.
4. Workspace policy is more specific than a system baseline.
5. Verification and evidence requirements are evaluated before financial approval.
6. Approval cannot turn a DENY or ESCALATE rule into ALLOW.
7. Approval is bound to the same requester and exact operation context.
8. Approval amount may cover a lower/equal amount, never a larger one.
9. Approval expires and must still be valid at re-evaluation.
10. Requester and approver must be different actors.
11. PROPERTY_MANAGEMENT is the current human-authority purpose.
12. AUTONOMOUS_EXECUTION is never silently treated as human authority.

## Deliberate boundary

Wave 09 builds the reusable policy/approval kernel and public seam. It does not yet rewrite every existing domain mutation to call the kernel. Domain-by-domain enforcement wiring follows as an explicit integration wave so policy context such as jurisdiction and amount is propagated honestly rather than guessed.

## Exit gate

Wave 09 may merge only after:
- Flyway V001 → V014 on PostgreSQL/PostGIS;
- Spring Modulith verification;
- all prior Golden Path / Decision / Reality / Guardian / Work Order / Service Graph regressions;
- autonomous approval lifecycle test;
- Four-Eyes rejection test;
- evidence/verification/financial-limit policy test;
- compliance API purpose/scope test;
- CodeQL green.
