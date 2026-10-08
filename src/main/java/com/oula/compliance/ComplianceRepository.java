package com.oula.compliance;

import com.oula.iam.AccessPurpose;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class ComplianceRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    ComplianceRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insertRule(PolicyRule rule) {
        jdbc.sql("""
                insert into compliance.policy_rule
                    (id, workspace_id, policy_key, version, applies_purpose,
                     action_pattern, resource_type_pattern, jurisdiction_pattern,
                     rule_effect, max_amount, currency, requires_verification,
                     requires_evidence, priority, status, effective_from,
                     effective_until, created_by, created_at)
                values
                    (:id, :workspaceId, :policyKey, :version, :purpose,
                     :actionPattern, :resourcePattern, :jurisdictionPattern,
                     :effect, :maxAmount, :currency, :requiresVerification,
                     :requiresEvidence, :priority, :status, :effectiveFrom,
                     :effectiveUntil, :createdBy, :createdAt)
                """)
                .param("id", rule.id())
                .param("workspaceId", rule.workspaceId())
                .param("policyKey", rule.policyKey())
                .param("version", rule.version())
                .param("purpose", rule.appliesPurpose().name())
                .param("actionPattern", rule.actionPattern())
                .param("resourcePattern", rule.resourceTypePattern())
                .param("jurisdictionPattern", rule.jurisdictionPattern())
                .param("effect", rule.ruleEffect().name())
                .param("maxAmount", rule.maxAmount())
                .param("currency", rule.currency())
                .param("requiresVerification", rule.requiresVerification())
                .param("requiresEvidence", rule.requiresEvidence())
                .param("priority", rule.priority())
                .param("status", rule.status())
                .param("effectiveFrom", utc(rule.effectiveFrom()))
                .param("effectiveUntil", utc(rule.effectiveUntil()))
                .param("createdBy", rule.createdBy())
                .param("createdAt", utc(rule.createdAt()))
                .update();
    }

    PolicyRule matchingRule(
            UUID workspaceId,
            AccessPurpose purpose,
            String action,
            String resourceType,
            String jurisdiction,
            Instant evaluatedAt
    ) {
        return jdbc.sql("""
                select *,
                       (case when workspace_id is not null then 1000 else 0 end)
                       + (case when action_pattern = :action then 100 else 0 end)
                       + (case when resource_type_pattern = :resourceType then 10 else 0 end)
                       + (case when jurisdiction_pattern = :jurisdiction then 1 else 0 end)
                         as specificity
                  from compliance.policy_rule
                 where status = 'ACTIVE'
                   and applies_purpose = :purpose
                   and (workspace_id = :workspaceId or workspace_id is null)
                   and (action_pattern = :action or action_pattern = '*')
                   and (resource_type_pattern = :resourceType or resource_type_pattern = '*')
                   and (jurisdiction_pattern = :jurisdiction or jurisdiction_pattern = '*')
                   and effective_from <= :evaluatedAt
                   and (effective_until is null or effective_until > :evaluatedAt)
                 order by specificity desc, priority desc, effective_from desc, id
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("purpose", purpose.name())
                .param("action", action)
                .param("resourceType", resourceType)
                .param("jurisdiction", jurisdiction)
                .param("evaluatedAt", utc(evaluatedAt))
                .query((rs, rowNum) -> mapRule(rs))
                .optional()
                .orElse(null);
    }

    void insertDecision(PolicyDecision decision, Map<String, Object> context) {
        jdbc.sql("""
                insert into compliance.policy_decision
                    (id, workspace_id, actor_id, purpose, action, resource_type,
                     resource_id, jurisdiction, amount, currency, policy_rule_id,
                     approval_request_id, decision, reason_codes, evaluated_at, context)
                values
                    (:id, :workspaceId, :actorId, :purpose, :action, :resourceType,
                     :resourceId, :jurisdiction, :amount, :currency, :policyRuleId,
                     :approvalRequestId, :decision, cast(:reasons as jsonb),
                     :evaluatedAt, cast(:context as jsonb))
                """)
                .param("id", decision.id())
                .param("workspaceId", decision.workspaceId())
                .param("actorId", decision.actorId())
                .param("purpose", decision.purpose().name())
                .param("action", decision.action())
                .param("resourceType", decision.resourceType())
                .param("resourceId", decision.resourceId())
                .param("jurisdiction", decision.jurisdiction())
                .param("amount", decision.amount())
                .param("currency", decision.currency())
                .param("policyRuleId", decision.policyRuleId())
                .param("approvalRequestId", decision.approvalRequestId())
                .param("decision", decision.decision().name())
                .param("reasons", write(decision.reasonCodes()))
                .param("evaluatedAt", utc(decision.evaluatedAt()))
                .param("context", write(context))
                .update();
    }

    ApprovalRequest approval(UUID workspaceId, UUID approvalRequestId) {
        return jdbc.sql("""
                select *
                  from compliance.approval_request
                 where id = :id
                   and workspace_id = :workspaceId
                """)
                .param("id", approvalRequestId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapApproval(rs))
                .optional()
                .orElse(null);
    }

    ApprovalRequest pendingApproval(
            UUID workspaceId,
            UUID policyRuleId,
            UUID requestedBy,
            AccessPurpose purpose,
            PolicyRequest request,
            Instant now
    ) {
        return jdbc.sql("""
                select *
                  from compliance.approval_request
                 where workspace_id = :workspaceId
                   and policy_rule_id = :policyRuleId
                   and requested_by = :requestedBy
                   and purpose = :purpose
                   and action = :action
                   and resource_type = :resourceType
                   and resource_id = :resourceId
                   and jurisdiction = :jurisdiction
                   and status = 'PENDING'
                   and expires_at > :now
                   and (
                       (:amount is null and amount is null)
                       or (:amount is not null and amount = :amount)
                   )
                   and (
                       (:currency is null and currency is null)
                       or (:currency is not null and currency = :currency)
                   )
                 order by requested_at desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("policyRuleId", policyRuleId)
                .param("requestedBy", requestedBy)
                .param("purpose", purpose.name())
                .param("action", request.action())
                .param("resourceType", request.resourceType())
                .param("resourceId", request.resourceId())
                .param("jurisdiction", request.jurisdiction())
                .param("amount", request.amount())
                .param("currency", request.currency())
                .param("now", utc(now))
                .query((rs, rowNum) -> mapApproval(rs))
                .optional()
                .orElse(null);
    }

    void insertApproval(ApprovalRequest approval) {
        jdbc.sql("""
                insert into compliance.approval_request
                    (id, workspace_id, policy_rule_id, policy_decision_id,
                     requested_by, purpose, action, resource_type, resource_id,
                     jurisdiction, amount, currency, justification, status,
                     requested_at, expires_at, version)
                values
                    (:id, :workspaceId, :policyRuleId, :policyDecisionId,
                     :requestedBy, :purpose, :action, :resourceType, :resourceId,
                     :jurisdiction, :amount, :currency, :justification, :status,
                     :requestedAt, :expiresAt, :version)
                """)
                .param("id", approval.id())
                .param("workspaceId", approval.workspaceId())
                .param("policyRuleId", approval.policyRuleId())
                .param("policyDecisionId", approval.policyDecisionId())
                .param("requestedBy", approval.requestedBy())
                .param("purpose", approval.purpose().name())
                .param("action", approval.action())
                .param("resourceType", approval.resourceType())
                .param("resourceId", approval.resourceId())
                .param("jurisdiction", approval.jurisdiction())
                .param("amount", approval.amount())
                .param("currency", approval.currency())
                .param("justification", approval.justification())
                .param("status", approval.status())
                .param("requestedAt", utc(approval.requestedAt()))
                .param("expiresAt", utc(approval.expiresAt()))
                .param("version", approval.version())
                .update();
    }

    ApprovalRequest lockApproval(UUID workspaceId, UUID approvalRequestId) {
        return jdbc.sql("""
                select *
                  from compliance.approval_request
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", approvalRequestId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapApproval(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("approval request not found"));
    }

    ApprovalRequest decide(
            ApprovalRequest current,
            String status,
            UUID actorId,
            String note,
            Instant decidedAt
    ) {
        int updated = jdbc.sql("""
                update compliance.approval_request
                   set status = :status,
                       decided_by = :actorId,
                       decided_at = :decidedAt,
                       decision_note = :note,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'PENDING'
                """)
                .param("status", status)
                .param("actorId", actorId)
                .param("decidedAt", utc(decidedAt))
                .param("note", note)
                .param("id", current.id())
                .param("version", current.version())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("approval request changed concurrently");
        }
        return new ApprovalRequest(
                current.id(), current.workspaceId(), current.policyRuleId(),
                current.policyDecisionId(), current.requestedBy(), current.purpose(),
                current.action(), current.resourceType(), current.resourceId(),
                current.jurisdiction(), current.amount(), current.currency(),
                current.justification(), status, current.requestedAt(), current.expiresAt(),
                actorId, decidedAt, note, current.version() + 1
        );
    }

    ApprovalRequest expire(ApprovalRequest current, Instant now) {
        int updated = jdbc.sql("""
                update compliance.approval_request
                   set status = 'EXPIRED',
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'PENDING'
                """)
                .param("id", current.id())
                .param("version", current.version())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("approval request changed concurrently");
        }
        return new ApprovalRequest(
                current.id(), current.workspaceId(), current.policyRuleId(),
                current.policyDecisionId(), current.requestedBy(), current.purpose(),
                current.action(), current.resourceType(), current.resourceId(),
                current.jurisdiction(), current.amount(), current.currency(),
                current.justification(), "EXPIRED", current.requestedAt(), current.expiresAt(),
                null, null, current.decisionNote(), current.version() + 1
        );
    }

    private PolicyRule mapRule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PolicyRule(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("policy_key"),
                rs.getString("version"),
                AccessPurpose.valueOf(rs.getString("applies_purpose")),
                rs.getString("action_pattern"),
                rs.getString("resource_type_pattern"),
                rs.getString("jurisdiction_pattern"),
                PolicyDecision.Decision.valueOf(rs.getString("rule_effect")),
                rs.getBigDecimal("max_amount"),
                rs.getString("currency"),
                rs.getBoolean("requires_verification"),
                rs.getBoolean("requires_evidence"),
                rs.getInt("priority"),
                rs.getString("status"),
                instant(rs.getObject("effective_from", OffsetDateTime.class)),
                instant(rs.getObject("effective_until", OffsetDateTime.class)),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class))
        );
    }

    private ApprovalRequest mapApproval(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApprovalRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("policy_rule_id", UUID.class),
                rs.getObject("policy_decision_id", UUID.class),
                rs.getObject("requested_by", UUID.class),
                AccessPurpose.valueOf(rs.getString("purpose")),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getObject("resource_id", UUID.class),
                rs.getString("jurisdiction"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("justification"),
                rs.getString("status"),
                instant(rs.getObject("requested_at", OffsetDateTime.class)),
                instant(rs.getObject("expires_at", OffsetDateTime.class)),
                rs.getObject("decided_by", UUID.class),
                instant(rs.getObject("decided_at", OffsetDateTime.class)),
                rs.getString("decision_note"),
                rs.getLong("version")
        );
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize compliance data", ex);
        }
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
