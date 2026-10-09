package com.oula.interventions;

import com.oula.vitals.VitalStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class InterventionRepository {
    private final JdbcClient jdbc;

    InterventionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertReview(InterventionReview review) {
        jdbc.sql("""
                insert into interventions.review (
                    id, workspace_id, property_id, source_snapshot_id,
                    source_policy_key, source_policy_version, advisory_rules_version,
                    dimension, action_code, baseline_status, decision, rationale,
                    reviewed_by, reviewed_at
                ) values (
                    :id, :workspaceId, :propertyId, :snapshotId,
                    :policyKey, :policyVersion, :rulesVersion,
                    :dimension, :actionCode, :baselineStatus, :decision, :rationale,
                    :reviewedBy, :reviewedAt
                )
                """)
                .param("id", review.id())
                .param("workspaceId", review.workspaceId())
                .param("propertyId", review.propertyId())
                .param("snapshotId", review.sourceSnapshotId())
                .param("policyKey", review.sourcePolicyKey())
                .param("policyVersion", review.sourcePolicyVersion())
                .param("rulesVersion", review.advisoryRulesVersion())
                .param("dimension", review.dimension())
                .param("actionCode", review.actionCode())
                .param("baselineStatus", review.baselineStatus().name())
                .param("decision", review.decision())
                .param("rationale", review.rationale())
                .param("reviewedBy", review.reviewedBy())
                .param("reviewedAt", utc(review.reviewedAt()))
                .update();
    }

    InterventionReview lockReview(UUID workspaceId, UUID reviewId) {
        return jdbc.sql("""
                select * from interventions.review
                 where workspace_id = :workspaceId and id = :reviewId
                 for update
                """)
                .param("workspaceId", workspaceId)
                .param("reviewId", reviewId)
                .query((rs, n) -> mapReview(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("intervention review not found"));
    }

    InterventionReview findReview(UUID workspaceId, UUID reviewId) {
        return jdbc.sql("""
                select * from interventions.review
                 where workspace_id = :workspaceId and id = :reviewId
                """)
                .param("workspaceId", workspaceId)
                .param("reviewId", reviewId)
                .query((rs, n) -> mapReview(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("intervention review not found"));
    }

    List<InterventionReview> listReviews(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select * from interventions.review
                 where workspace_id = :workspaceId and property_id = :propertyId
                 order by reviewed_at desc, id desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs,n) -> mapReview(rs))
                .list();
    }

    void insertOutcome(InterventionOutcome outcome) {
        jdbc.sql("""
                insert into interventions.outcome_observation (
                    id, workspace_id, property_id, review_id, after_snapshot_id,
                    work_order_id, before_status, after_status, observed_direction,
                    execution_evidence_level, note, observed_by, observed_at
                ) values (
                    :id, :workspaceId, :propertyId, :reviewId, :afterSnapshotId,
                    :workOrderId, :beforeStatus, :afterStatus, :direction,
                    :evidenceLevel, :note, :observedBy, :observedAt
                )
                """)
                .param("id", outcome.id())
                .param("workspaceId", outcome.workspaceId())
                .param("propertyId", outcome.propertyId())
                .param("reviewId", outcome.reviewId())
                .param("afterSnapshotId", outcome.afterSnapshotId())
                .param("workOrderId", outcome.workOrderId())
                .param("beforeStatus", outcome.beforeStatus().name())
                .param("afterStatus", outcome.afterStatus().name())
                .param("direction", outcome.observedDirection())
                .param("evidenceLevel", outcome.executionEvidenceLevel())
                .param("note", outcome.note())
                .param("observedBy", outcome.observedBy())
                .param("observedAt", utc(outcome.observedAt()))
                .update();
    }

    List<InterventionOutcome> listOutcomes(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select * from interventions.outcome_observation
                 where workspace_id = :workspaceId and property_id = :propertyId
                 order by observed_at desc, id desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs,n) -> mapOutcome(rs))
                .list();
    }

    private InterventionReview mapReview(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InterventionReview(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("source_snapshot_id", UUID.class),
                rs.getString("source_policy_key"),
                rs.getString("source_policy_version"),
                rs.getString("advisory_rules_version"),
                rs.getString("dimension"),
                rs.getString("action_code"),
                VitalStatus.valueOf(rs.getString("baseline_status")),
                rs.getString("decision"),
                rs.getString("rationale"),
                rs.getObject("reviewed_by", UUID.class),
                instant(rs.getObject("reviewed_at", OffsetDateTime.class))
        );
    }

    private InterventionOutcome mapOutcome(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InterventionOutcome(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("review_id", UUID.class),
                rs.getObject("after_snapshot_id", UUID.class),
                rs.getObject("work_order_id", UUID.class),
                VitalStatus.valueOf(rs.getString("before_status")),
                VitalStatus.valueOf(rs.getString("after_status")),
                rs.getString("observed_direction"),
                rs.getString("execution_evidence_level"),
                rs.getString("note"),
                rs.getObject("observed_by", UUID.class),
                instant(rs.getObject("observed_at", OffsetDateTime.class))
        );
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
