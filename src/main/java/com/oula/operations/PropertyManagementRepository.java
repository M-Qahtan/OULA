package com.oula.operations;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class PropertyManagementRepository {
    private final JdbcClient jdbc;

    PropertyManagementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void lockProperty(UUID workspaceId, UUID propertyId) {
        jdbc.sql("""
                select id
                  from property.asset
                 where id = :propertyId
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property not found"));
    }

    ManagementEnrollment activeEnrollment(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, manager_actor_id, status,
                       activated_at, closed_at, version
                  from ops.management_enrollment
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                   and status = 'ACTIVE'
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> new ManagementEnrollment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("property_id", UUID.class),
                        rs.getObject("manager_actor_id", UUID.class),
                        rs.getString("status"),
                        instant(rs.getObject("activated_at", OffsetDateTime.class)),
                        instant(rs.getObject("closed_at", OffsetDateTime.class)),
                        rs.getLong("version")
                ))
                .optional()
                .orElse(null);
    }

    void insertEnrollment(ManagementEnrollment enrollment) {
        jdbc.sql("""
                insert into ops.management_enrollment
                    (id, workspace_id, property_id, manager_actor_id, status, activated_at, version)
                values
                    (:id, :workspaceId, :propertyId, :managerActorId, :status, :activatedAt, :version)
                """)
                .param("id", enrollment.id())
                .param("workspaceId", enrollment.workspaceId())
                .param("propertyId", enrollment.propertyId())
                .param("managerActorId", enrollment.managerActorId())
                .param("status", enrollment.status())
                .param("activatedAt", utc(enrollment.activatedAt()))
                .param("version", enrollment.version())
                .update();
    }

    void insertObligation(Obligation obligation) {
        jdbc.sql("""
                insert into ops.obligation
                    (id, workspace_id, property_id, obligation_type, title, due_at,
                     priority, status, source_type, source_reference, created_by,
                     created_at, version)
                values
                    (:id, :workspaceId, :propertyId, :type, :title, :dueAt,
                     :priority, :status, :sourceType, :sourceReference, :createdBy,
                     :createdAt, :version)
                """)
                .param("id", obligation.id())
                .param("workspaceId", obligation.workspaceId())
                .param("propertyId", obligation.propertyId())
                .param("type", obligation.obligationType())
                .param("title", obligation.title())
                .param("dueAt", utc(obligation.dueAt()))
                .param("priority", obligation.priority())
                .param("status", obligation.status())
                .param("sourceType", obligation.sourceType())
                .param("sourceReference", obligation.sourceReference())
                .param("createdBy", obligation.createdBy())
                .param("createdAt", utc(obligation.createdAt()))
                .param("version", obligation.version())
                .update();
    }

    List<Obligation> dueObligations(UUID workspaceId, UUID propertyId, Instant horizon) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_type, title, due_at,
                       priority, status, source_type, source_reference, created_by,
                       created_at, completed_at, version
                  from ops.obligation
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                   and status = 'OPEN'
                   and due_at <= :horizon
                 order by due_at, priority desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("horizon", utc(horizon))
                .query((rs, rowNum) -> mapObligation(rs))
                .list();
    }

    List<Obligation> obligations(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_type, title, due_at,
                       priority, status, source_type, source_reference, created_by,
                       created_at, completed_at, version
                  from ops.obligation
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by due_at, created_at
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> mapObligation(rs))
                .list();
    }

    GuardianSignal signalForObligation(UUID obligationId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_id, signal_type,
                       severity, status, message, recommended_action, detected_at,
                       resolved_at, version
                  from ops.guardian_signal
                 where obligation_id = :obligationId
                """)
                .param("obligationId", obligationId)
                .query((rs, rowNum) -> mapSignal(rs))
                .optional()
                .orElse(null);
    }

    GuardianSignal insertSignal(
            UUID id,
            Obligation obligation,
            String severity,
            String message,
            String recommendedAction,
            Instant detectedAt
    ) {
        jdbc.sql("""
                insert into ops.guardian_signal
                    (id, workspace_id, property_id, obligation_id, signal_type, severity,
                     status, message, recommended_action, detected_at, evidence, version)
                values
                    (:id, :workspaceId, :propertyId, :obligationId, 'OBLIGATION_DUE',
                     :severity, 'OPEN', :message, :recommendedAction, :detectedAt,
                     '{}'::jsonb, 0)
                """)
                .param("id", id)
                .param("workspaceId", obligation.workspaceId())
                .param("propertyId", obligation.propertyId())
                .param("obligationId", obligation.id())
                .param("severity", severity)
                .param("message", message)
                .param("recommendedAction", recommendedAction)
                .param("detectedAt", utc(detectedAt))
                .update();

        return new GuardianSignal(
                id, obligation.workspaceId(), obligation.propertyId(), obligation.id(),
                "OBLIGATION_DUE", severity, "OPEN", message, recommendedAction,
                detectedAt, null, 0
        );
    }

    ActionItem actionForObligation(UUID obligationId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_id, guardian_signal_id,
                       action_type, title, status, due_at, assigned_actor_id,
                       resolution_note, created_at, completed_at, version
                  from ops.action_item
                 where obligation_id = :obligationId
                   and action_type = 'FULFILL_OBLIGATION'
                """)
                .param("obligationId", obligationId)
                .query((rs, rowNum) -> mapAction(rs))
                .optional()
                .orElse(null);
    }

    ActionItem insertAction(
            UUID id,
            Obligation obligation,
            GuardianSignal signal,
            UUID assignedActorId,
            Instant createdAt
    ) {
        jdbc.sql("""
                insert into ops.action_item
                    (id, workspace_id, property_id, obligation_id, guardian_signal_id,
                     action_type, title, status, due_at, assigned_actor_id, created_at, version)
                values
                    (:id, :workspaceId, :propertyId, :obligationId, :signalId,
                     'FULFILL_OBLIGATION', :title, 'OPEN', :dueAt, :assignedActorId,
                     :createdAt, 0)
                """)
                .param("id", id)
                .param("workspaceId", obligation.workspaceId())
                .param("propertyId", obligation.propertyId())
                .param("obligationId", obligation.id())
                .param("signalId", signal.id())
                .param("title", "Fulfill: " + obligation.title())
                .param("dueAt", utc(obligation.dueAt()))
                .param("assignedActorId", assignedActorId)
                .param("createdAt", utc(createdAt))
                .update();

        return new ActionItem(
                id, obligation.workspaceId(), obligation.propertyId(), obligation.id(),
                signal.id(), "FULFILL_OBLIGATION", "Fulfill: " + obligation.title(),
                "OPEN", obligation.dueAt(), assignedActorId, null, createdAt, null, 0
        );
    }

    List<GuardianSignal> signals(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_id, signal_type,
                       severity, status, message, recommended_action, detected_at,
                       resolved_at, version
                  from ops.guardian_signal
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by detected_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> mapSignal(rs))
                .list();
    }

    List<ActionItem> actions(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_id, guardian_signal_id,
                       action_type, title, status, due_at, assigned_actor_id,
                       resolution_note, created_at, completed_at, version
                  from ops.action_item
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by created_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> mapAction(rs))
                .list();
    }

    ActionItem lockAction(UUID workspaceId, UUID actionId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, obligation_id, guardian_signal_id,
                       action_type, title, status, due_at, assigned_actor_id,
                       resolution_note, created_at, completed_at, version
                  from ops.action_item
                 where id = :actionId
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("actionId", actionId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapAction(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("action not found"));
    }

    ActionItem completeAction(ActionItem action, String resolutionNote, Instant completedAt) {
        if ("COMPLETED".equals(action.status())) {
            return action;
        }
        jdbc.sql("""
                update ops.action_item
                   set status = 'COMPLETED',
                       resolution_note = :note,
                       completed_at = :completedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                """)
                .param("note", resolutionNote)
                .param("completedAt", utc(completedAt))
                .param("id", action.id())
                .param("version", action.version())
                .update();

        if (action.obligationId() != null) {
            jdbc.sql("""
                    update ops.obligation
                       set status = 'SATISFIED',
                           completed_at = :completedAt,
                           version = version + 1
                     where id = :id
                       and status = 'OPEN'
                    """)
                    .param("completedAt", utc(completedAt))
                    .param("id", action.obligationId())
                    .update();
        }
        if (action.guardianSignalId() != null) {
            jdbc.sql("""
                    update ops.guardian_signal
                       set status = 'RESOLVED',
                           resolved_at = :completedAt,
                           version = version + 1
                     where id = :id
                       and status = 'OPEN'
                    """)
                    .param("completedAt", utc(completedAt))
                    .param("id", action.guardianSignalId())
                    .update();
        }

        return new ActionItem(
                action.id(), action.workspaceId(), action.propertyId(),
                action.obligationId(), action.guardianSignalId(), action.actionType(),
                action.title(), "COMPLETED", action.dueAt(), action.assignedActorId(),
                resolutionNote, action.createdAt(), completedAt, action.version() + 1
        );
    }

    private Obligation mapObligation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Obligation(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getString("obligation_type"),
                rs.getString("title"),
                instant(rs.getObject("due_at", OffsetDateTime.class)),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getString("source_type"),
                rs.getString("source_reference"),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private GuardianSignal mapSignal(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GuardianSignal(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("obligation_id", UUID.class),
                rs.getString("signal_type"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("recommended_action"),
                instant(rs.getObject("detected_at", OffsetDateTime.class)),
                instant(rs.getObject("resolved_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private ActionItem mapAction(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ActionItem(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("obligation_id", UUID.class),
                rs.getObject("guardian_signal_id", UUID.class),
                rs.getString("action_type"),
                rs.getString("title"),
                rs.getString("status"),
                instant(rs.getObject("due_at", OffsetDateTime.class)),
                rs.getObject("assigned_actor_id", UUID.class),
                rs.getString("resolution_note"),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
