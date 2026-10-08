package com.oula.compliance;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ComplianceKernel {
    private static final Duration APPROVAL_TTL = Duration.ofHours(24);

    private final ComplianceRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public ComplianceKernel(
            ComplianceRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public PolicyRule createRule(
            AccessContext access,
            CreatePolicyRuleCommand command,
            UUID correlationId
    ) {
        requireHumanManagement(access);
        Objects.requireNonNull(command, "command");
        requireText(command.policyKey(), "policyKey");
        requireText(command.version(), "version");
        Objects.requireNonNull(command.appliesPurpose(), "appliesPurpose");
        requirePattern(command.actionPattern(), "actionPattern");
        requirePattern(command.resourceTypePattern(), "resourceTypePattern");
        requirePattern(command.jurisdictionPattern(), "jurisdictionPattern");
        Objects.requireNonNull(command.ruleEffect(), "ruleEffect");
        if (command.priority() < -1000 || command.priority() > 1000) {
            throw new IllegalArgumentException("priority must be between -1000 and 1000");
        }
        if (command.maxAmount() != null) {
            requireMoney(command.maxAmount(), "maxAmount");
            requireText(command.currency(), "currency");
        }

        Instant now = clock.instant();
        Instant effectiveFrom = command.effectiveFrom() == null ? now : command.effectiveFrom();
        if (command.effectiveUntil() != null
                && !command.effectiveUntil().isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }

        PolicyRule rule = new PolicyRule(
                UuidV7.next(),
                access.workspaceId(),
                command.policyKey().trim(),
                command.version().trim(),
                command.appliesPurpose(),
                command.actionPattern().trim(),
                command.resourceTypePattern().trim(),
                command.jurisdictionPattern().trim().toUpperCase(),
                command.ruleEffect(),
                command.maxAmount(),
                command.maxAmount() == null ? null : normalizeCurrency(command.currency()),
                command.requiresVerification(),
                command.requiresEvidence(),
                command.priority(),
                "ACTIVE",
                effectiveFrom,
                command.effectiveUntil(),
                access.actorId(),
                now
        );
        repository.insertRule(rule);

        Map<String, Object> details = Map.of(
                "policyKey", rule.policyKey(),
                "version", rule.version(),
                "appliesPurpose", rule.appliesPurpose().name(),
                "effect", rule.ruleEffect().name(),
                "priority", rule.priority()
        );
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), "COMPLIANCE_POLICY_RULE_CREATED",
                "PolicyRule", rule.id(), correlationId, details);
        outbox.append("compliance.policy_rule.created.v1", "PolicyRule", rule.id(),
                access.workspaceId(), correlationId, correlationId, details);
        return rule;
    }

    @Transactional
    public PolicyDecision evaluate(
            AccessContext access,
            PolicyRequest request,
            UUID correlationId
    ) {
        Objects.requireNonNull(access, "access");
        validateRequest(request);
        Instant now = clock.instant();
        String jurisdiction = normalizeJurisdiction(request.jurisdiction());
        String currency = request.amount() == null ? null : normalizeCurrency(request.currency());

        PolicyRule rule = repository.matchingRule(
                access.workspaceId(), access.purpose(),
                request.action().trim(), request.resourceType().trim(),
                jurisdiction, now
        );

        PolicyDecision.Decision decision;
        List<String> reasons = new ArrayList<>();
        UUID policyRuleId = rule == null ? null : rule.id();

        if (rule == null) {
            decision = PolicyDecision.Decision.DENY;
            reasons.add("NO_MATCHING_POLICY");
        } else if (rule.currency() != null
                && request.amount() != null
                && !rule.currency().equals(currency)) {
            decision = PolicyDecision.Decision.DENY;
            reasons.add("POLICY_CURRENCY_MISMATCH");
        } else if (rule.requiresVerification() && !request.verificationPresent()) {
            decision = PolicyDecision.Decision.REQUIRE_VERIFICATION;
            reasons.add("VERIFICATION_REQUIRED");
        } else if (rule.requiresEvidence() && !request.evidencePresent()) {
            decision = PolicyDecision.Decision.REQUIRE_DOCUMENT;
            reasons.add("EVIDENCE_REQUIRED");
        } else if (rule.ruleEffect() == PolicyDecision.Decision.DENY) {
            decision = PolicyDecision.Decision.DENY;
            reasons.add("POLICY_DENY");
        } else if (rule.ruleEffect() == PolicyDecision.Decision.ESCALATE) {
            decision = PolicyDecision.Decision.ESCALATE;
            reasons.add("POLICY_ESCALATION");
        } else if (rule.ruleEffect() == PolicyDecision.Decision.REQUIRE_DOCUMENT) {
            decision = PolicyDecision.Decision.REQUIRE_DOCUMENT;
            reasons.add("POLICY_DOCUMENT_REQUIRED");
        } else if (rule.ruleEffect() == PolicyDecision.Decision.REQUIRE_VERIFICATION) {
            decision = PolicyDecision.Decision.REQUIRE_VERIFICATION;
            reasons.add("POLICY_VERIFICATION_REQUIRED");
        } else if (requiresApproval(rule, request)) {
            if (approvalSatisfies(access, rule, request, now)) {
                decision = PolicyDecision.Decision.ALLOW;
                reasons.add("APPROVAL_SATISFIED");
            } else {
                decision = PolicyDecision.Decision.REQUIRE_APPROVAL;
                reasons.add(rule.ruleEffect() == PolicyDecision.Decision.REQUIRE_APPROVAL
                        ? "POLICY_APPROVAL_REQUIRED"
                        : "FINANCIAL_LIMIT_EXCEEDED");
            }
        } else {
            decision = PolicyDecision.Decision.ALLOW;
            reasons.add("POLICY_ALLOW");
        }

        PolicyDecision result = new PolicyDecision(
                UuidV7.next(), access.workspaceId(), access.actorId(), access.purpose(),
                request.action().trim(), request.resourceType().trim(), request.resourceId(),
                jurisdiction, request.amount(), currency, policyRuleId,
                request.approvalRequestId(), decision, reasons, now
        );
        repository.insertDecision(result, request.context());

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("action", result.action());
        details.put("resourceType", result.resourceType());
        details.put("resourceId", result.resourceId());
        details.put("jurisdiction", result.jurisdiction());
        details.put("decision", result.decision().name());
        details.put("reasonCodes", result.reasonCodes());
        if (result.policyRuleId() != null) details.put("policyRuleId", result.policyRuleId());
        if (result.approvalRequestId() != null) details.put("approvalRequestId", result.approvalRequestId());

        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), "COMPLIANCE_POLICY_EVALUATED",
                result.resourceType(), result.resourceId(), correlationId, details);
        outbox.append("compliance.policy_decision.evaluated.v1",
                result.resourceType(), result.resourceId(), access.workspaceId(),
                correlationId, correlationId, details);
        return result;
    }

    @Transactional
    public PolicyDecision requireAllowed(
            AccessContext access,
            PolicyRequest request,
            UUID correlationId
    ) {
        PolicyDecision decision = evaluate(access, request, correlationId);
        if (!decision.allowed()) {
            throw new PolicyEnforcementException(decision);
        }
        return decision;
    }

    @Transactional
    public ApprovalRequest requestApproval(
            AccessContext access,
            PolicyRequest request,
            String justification,
            UUID correlationId
    ) {
        requireText(justification, "justification");
        PolicyRequest withoutApproval = new PolicyRequest(
                request.action(), request.resourceType(), request.resourceId(),
                request.jurisdiction(), request.amount(), request.currency(),
                request.verificationPresent(), request.evidencePresent(),
                null, request.context()
        );
        PolicyDecision decision = evaluate(access, withoutApproval, correlationId);
        if (decision.decision() != PolicyDecision.Decision.REQUIRE_APPROVAL
                || decision.policyRuleId() == null) {
            throw new IllegalStateException("current policy does not require approval");
        }

        Instant now = clock.instant();
        ApprovalRequest existing = repository.pendingApproval(
                access.workspaceId(), decision.policyRuleId(), access.actorId(),
                access.purpose(), withoutApproval, now
        );
        if (existing != null) {
            return existing;
        }

        ApprovalRequest approval = new ApprovalRequest(
                UuidV7.next(), access.workspaceId(), decision.policyRuleId(),
                decision.id(), access.actorId(), access.purpose(),
                decision.action(), decision.resourceType(), decision.resourceId(),
                decision.jurisdiction(), decision.amount(), decision.currency(),
                justification.trim(), "PENDING", now, now.plus(APPROVAL_TTL),
                null, null, null, 0
        );
        repository.insertApproval(approval);

        Map<String, Object> details = Map.of(
                "action", approval.action(),
                "resourceType", approval.resourceType(),
                "resourceId", approval.resourceId(),
                "requestedBy", approval.requestedBy(),
                "expiresAt", approval.expiresAt().toString()
        );
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), "COMPLIANCE_APPROVAL_REQUESTED",
                "ApprovalRequest", approval.id(), correlationId, details);
        outbox.append("compliance.approval.requested.v1", "ApprovalRequest",
                approval.id(), access.workspaceId(), correlationId,
                correlationId, details);
        return approval;
    }

    @Transactional
    public ApprovalRequest decideApproval(
            AccessContext access,
            UUID approvalRequestId,
            boolean approve,
            String note,
            UUID correlationId
    ) {
        requireHumanManagement(access);
        requireText(note, "note");
        ApprovalRequest current = repository.lockApproval(
                access.workspaceId(), approvalRequestId
        );
        if (!"PENDING".equals(current.status())) {
            throw new IllegalStateException("approval request is not pending");
        }

        Instant now = clock.instant();
        if (!current.expiresAt().isAfter(now)) {
            repository.expire(current, now);
            throw new IllegalStateException("approval request has expired");
        }
        if (current.requestedBy().equals(access.actorId())) {
            throw new SecurityException("requester cannot approve or reject the same request");
        }

        String status = approve ? "APPROVED" : "REJECTED";
        ApprovalRequest decided = repository.decide(
                current, status, access.actorId(), note.trim(), now
        );

        Map<String, Object> details = Map.of(
                "status", status,
                "requestedBy", decided.requestedBy(),
                "decidedBy", access.actorId(),
                "action", decided.action(),
                "resourceId", decided.resourceId()
        );
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), "COMPLIANCE_APPROVAL_DECIDED",
                "ApprovalRequest", decided.id(), correlationId, details);
        outbox.append("compliance.approval.decided.v1", "ApprovalRequest",
                decided.id(), access.workspaceId(), correlationId,
                correlationId, details);
        return decided;
    }

    private boolean requiresApproval(PolicyRule rule, PolicyRequest request) {
        if (rule.ruleEffect() == PolicyDecision.Decision.REQUIRE_APPROVAL) {
            return true;
        }
        return rule.maxAmount() != null
                && request.amount() != null
                && request.amount().compareTo(rule.maxAmount()) > 0;
    }

    private boolean approvalSatisfies(
            AccessContext access,
            PolicyRule rule,
            PolicyRequest request,
            Instant now
    ) {
        if (request.approvalRequestId() == null) {
            return false;
        }
        ApprovalRequest approval = repository.approval(
                access.workspaceId(), request.approvalRequestId()
        );
        if (approval == null
                || !"APPROVED".equals(approval.status())
                || !approval.expiresAt().isAfter(now)
                || !approval.policyRuleId().equals(rule.id())
                || !approval.requestedBy().equals(access.actorId())
                || approval.purpose() != access.purpose()
                || !approval.action().equals(request.action().trim())
                || !approval.resourceType().equals(request.resourceType().trim())
                || !approval.resourceId().equals(request.resourceId())
                || !approval.jurisdiction().equals(normalizeJurisdiction(request.jurisdiction()))) {
            return false;
        }

        if (request.amount() == null) {
            return approval.amount() == null;
        }
        if (approval.amount() == null || approval.amount().compareTo(request.amount()) < 0) {
            return false;
        }
        String requestCurrency = normalizeCurrency(request.currency());
        return Objects.equals(approval.currency(), requestCurrency);
    }

    private void validateRequest(PolicyRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.action(), "action");
        requireText(request.resourceType(), "resourceType");
        Objects.requireNonNull(request.resourceId(), "resourceId");
        requireText(request.jurisdiction(), "jurisdiction");
        if (request.amount() != null) {
            requireMoney(request.amount(), "amount");
            requireText(request.currency(), "currency");
        } else if (request.currency() != null && !request.currency().isBlank()) {
            throw new IllegalArgumentException("currency requires amount");
        }
    }

    private void requireHumanManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
    }

    private String normalizeJurisdiction(String jurisdiction) {
        requireText(jurisdiction, "jurisdiction");
        return jurisdiction.trim().toUpperCase();
    }

    private String normalizeCurrency(String currency) {
        requireText(currency, "currency");
        return Currency.getInstance(currency.trim().toUpperCase()).getCurrencyCode();
    }

    private void requirePattern(String value, String field) {
        requireText(value, field);
        String trimmed = value.trim();
        if (trimmed.contains("*") && !"*".equals(trimmed)) {
            throw new IllegalArgumentException(field + " supports exact value or * only");
        }
        if (trimmed.contains("?")) {
            throw new IllegalArgumentException(field + " supports exact value or * only");
        }
    }

    private void requireMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
