# OULA Trust & Compliance Kernel

## Purpose

The Trust & Compliance Kernel is the policy enforcement foundation for future autonomous agents, regulated integrations and high-impact actions. It does not replace IAM, domain invariants, legal review or external regulator authority.

## Decision model

Inputs:
- actor and subject;
- workspace;
- purpose;
- action;
- resource type and resource identity;
- jurisdiction;
- amount and currency when financially material;
- evidence-present and verification-present flags;
- optional approval request;
- structured context.

Output:
- ALLOW
- DENY
- REQUIRE_APPROVAL
- REQUIRE_DOCUMENT
- REQUIRE_VERIFICATION
- ESCALATE

Every decision is persisted.

## Rule matching

Rules contain no executable expressions. Action, resource type and jurisdiction use only exact values or the single wildcard '*'.

Precedence:
1. workspace-specific rule over system rule;
2. more exact action/resource/jurisdiction over wildcard;
3. higher priority;
4. newer effective rule.

If nothing matches, the outcome is DENY.

## Autonomous authority

AUTONOMOUS_EXECUTION is intentionally distinct from PROPERTY_MANAGEMENT.

The system baseline for AUTONOMOUS_EXECUTION is REQUIRE_APPROVAL. An autonomous agent can request approval, but the approval must be decided by a different human actor under PROPERTY_MANAGEMENT authority.

Approval is context-bound and expires. Reuse for a different action, resource, jurisdiction, currency or larger amount is rejected during re-evaluation.

## Financial authority

A rule can define max_amount + currency. When an otherwise-ALLOW rule is evaluated above that amount, the result becomes REQUIRE_APPROVAL.

Financial approval does not move money. Payment execution remains outside this kernel and must pass its own regulated integration boundary.

## Evidence and verification

requires_verification and requires_evidence are evaluated before financial approval. A human approval cannot override a DENY, ESCALATE, missing verification requirement or missing evidence requirement.

## Integration rule

Domain services must eventually call the kernel with truthful context before high-impact mutations. They must not invent jurisdiction, amount or verification state merely to satisfy the kernel. Context propagation is therefore integrated domain-by-domain.


## Domain enforcement — Wave 10

The kernel is enforced inside domain services for the first material autonomous seams:
- WORK_ORDER_APPROVE;
- WORK_ORDER_ASSIGN;
- WORK_ORDER_START;
- SETTLEMENT_REFERENCE_RECORD.

A caller cannot bypass policy by skipping the HTTP controller. Each mutation persists/evaluates a policy decision first.

Human-only boundaries remain:
- Work Order creation;
- completion submission;
- completion verification.

Completion verification is intentionally excluded from AUTONOMOUS_EXECUTION so the executing agent cannot attest to its own result.

API calls may supply X-OULA-Jurisdiction and X-OULA-Approval-ID. Missing jurisdiction is represented explicitly as UNSPECIFIED for compatibility; it is not inferred.
