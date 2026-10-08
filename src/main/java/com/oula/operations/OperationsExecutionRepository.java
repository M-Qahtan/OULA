package com.oula.operations;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class OperationsExecutionRepository {
    private final JdbcClient jdbc;

    OperationsExecutionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    WorkOrder activeForAction(UUID workspaceId, UUID actionItemId) {
        return jdbc.sql("""
                select *
                  from ops.work_order
                 where workspace_id = :workspaceId
                   and action_item_id = :actionItemId
                   and status <> 'CANCELLED'
                """)
                .param("workspaceId", workspaceId)
                .param("actionItemId", actionItemId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElse(null);
    }

    void insert(WorkOrder workOrder) {
        jdbc.sql("""
                insert into ops.work_order
                    (id, workspace_id, property_id, action_item_id, category, title,
                     scope_description, status, estimated_cost, currency, created_by,
                     created_at, version)
                values
                    (:id, :workspaceId, :propertyId, :actionItemId, :category, :title,
                     :scopeDescription, :status, :estimatedCost, :currency, :createdBy,
                     :createdAt, :version)
                """)
                .param("id", workOrder.id())
                .param("workspaceId", workOrder.workspaceId())
                .param("propertyId", workOrder.propertyId())
                .param("actionItemId", workOrder.actionItemId())
                .param("category", workOrder.category())
                .param("title", workOrder.title())
                .param("scopeDescription", workOrder.scopeDescription())
                .param("status", workOrder.status())
                .param("estimatedCost", workOrder.estimatedCost())
                .param("currency", workOrder.currency())
                .param("createdBy", workOrder.createdBy())
                .param("createdAt", utc(workOrder.createdAt()))
                .param("version", workOrder.version())
                .update();
    }

    WorkOrder lock(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from ops.work_order
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", workOrderId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("work order not found"));
    }

    WorkOrder get(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from ops.work_order
                 where id = :id
                   and workspace_id = :workspaceId
                """)
                .param("id", workOrderId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("work order not found"));
    }

    List<WorkOrder> list(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select *
                  from ops.work_order
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by created_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    WorkOrder approve(WorkOrder current, UUID actorId, BigDecimal approvedBudget, Instant approvedAt) {
        requireUpdated(jdbc.sql("""
                update ops.work_order
                   set status = 'APPROVED',
                       approved_budget = :approvedBudget,
                       approval_actor_id = :actorId,
                       approved_at = :approvedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'PENDING_APPROVAL'
                """)
                .param("approvedBudget", approvedBudget)
                .param("actorId", actorId)
                .param("approvedAt", utc(approvedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update());
        return copy(current, "APPROVED", null, approvedBudget, null, actorId, approvedAt,
                null, null, null, null, null, current.version() + 1);
    }

    WorkOrder assign(WorkOrder current, UUID providerPartyId) {
        requireUpdated(jdbc.sql("""
                update ops.work_order
                   set status = 'ASSIGNED',
                       provider_party_id = :providerPartyId,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'APPROVED'
                """)
                .param("providerPartyId", providerPartyId)
                .param("id", current.id())
                .param("version", current.version())
                .update());
        return copy(current, "ASSIGNED", providerPartyId, current.approvedBudget(), null,
                current.approvalActorId(), current.approvedAt(), null, null, null, null, null,
                current.version() + 1);
    }

    WorkOrder start(WorkOrder current, Instant startedAt) {
        requireUpdated(jdbc.sql("""
                update ops.work_order
                   set status = 'IN_PROGRESS',
                       started_at = :startedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'ASSIGNED'
                """)
                .param("startedAt", utc(startedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update());
        return copy(current, "IN_PROGRESS", current.providerPartyId(), current.approvedBudget(), null,
                current.approvalActorId(), current.approvedAt(), startedAt, null, null, null, null,
                current.version() + 1);
    }

    WorkOrder submitCompletion(
            WorkOrder current,
            BigDecimal actualCost,
            UUID evidenceId,
            String completionNote,
            Instant submittedAt
    ) {
        requireUpdated(jdbc.sql("""
                update ops.work_order
                   set status = 'COMPLETION_REVIEW',
                       actual_cost = :actualCost,
                       completion_evidence_id = :evidenceId,
                       completion_note = :completionNote,
                       completion_submitted_at = :submittedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'IN_PROGRESS'
                """)
                .param("actualCost", actualCost)
                .param("evidenceId", evidenceId)
                .param("completionNote", completionNote)
                .param("submittedAt", utc(submittedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update());
        return copy(current, "COMPLETION_REVIEW", current.providerPartyId(),
                current.approvedBudget(), actualCost, current.approvalActorId(), current.approvedAt(),
                current.startedAt(), submittedAt, evidenceId, completionNote, null,
                current.version() + 1);
    }

    WorkOrder complete(WorkOrder current, Instant completedAt) {
        requireUpdated(jdbc.sql("""
                update ops.work_order
                   set status = 'COMPLETED',
                       completed_at = :completedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'COMPLETION_REVIEW'
                """)
                .param("completedAt", utc(completedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update());
        return copy(current, "COMPLETED", current.providerPartyId(), current.approvedBudget(),
                current.actualCost(), current.approvalActorId(), current.approvedAt(),
                current.startedAt(), current.completionSubmittedAt(), current.completionEvidenceId(),
                current.completionNote(), completedAt, current.version() + 1);
    }

    private WorkOrder copy(
            WorkOrder current,
            String status,
            UUID providerPartyId,
            BigDecimal approvedBudget,
            BigDecimal actualCost,
            UUID approvalActorId,
            Instant approvedAt,
            Instant startedAt,
            Instant completionSubmittedAt,
            UUID completionEvidenceId,
            String completionNote,
            Instant completedAt,
            long version
    ) {
        return new WorkOrder(
                current.id(), current.workspaceId(), current.propertyId(), current.actionItemId(),
                current.category(), current.title(), current.scopeDescription(), status,
                providerPartyId, current.estimatedCost(), approvedBudget, actualCost, current.currency(),
                approvalActorId, approvedAt, startedAt, completionSubmittedAt, completionEvidenceId,
                completionNote, completedAt, current.createdBy(), current.createdAt(), version
        );
    }

    private WorkOrder map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WorkOrder(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("action_item_id", UUID.class),
                rs.getString("category"),
                rs.getString("title"),
                rs.getString("scope_description"),
                rs.getString("status"),
                rs.getObject("provider_party_id", UUID.class),
                rs.getBigDecimal("estimated_cost"),
                rs.getBigDecimal("approved_budget"),
                rs.getBigDecimal("actual_cost"),
                rs.getString("currency"),
                rs.getObject("approval_actor_id", UUID.class),
                instant(rs.getObject("approved_at", OffsetDateTime.class)),
                instant(rs.getObject("started_at", OffsetDateTime.class)),
                instant(rs.getObject("completion_submitted_at", OffsetDateTime.class)),
                rs.getObject("completion_evidence_id", UUID.class),
                rs.getString("completion_note"),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("work order state changed concurrently or transition is invalid");
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
