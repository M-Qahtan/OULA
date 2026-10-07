package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class RealityGapRepository {
    private final JdbcClient jdbc;

    RealityGapRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(RealityGapView gap, UUID workspaceId, UUID correlationId) {
        jdbc.sql("""
                insert into intelligence.reality_gap
                    (id, workspace_id, outcome_id, decision_id, recommendation_id,
                     model_version_id, metric_key, unit, expected_value, actual_value,
                     signed_error, absolute_error, relative_error, policy_key, policy_version,
                     classification, cause_category, review_status, calibration_status,
                     detected_at, correlation_id)
                values
                    (:id, :workspaceId, :outcomeId, :decisionId, :recommendationId,
                     :modelVersionId, :metricKey, :unit, :expectedValue, :actualValue,
                     :signedError, :absoluteError, :relativeError, :policyKey, :policyVersion,
                     :classification, :causeCategory, :reviewStatus, :calibrationStatus,
                     :detectedAt, :correlationId)
                """)
                .param("id", gap.gapId())
                .param("workspaceId", workspaceId)
                .param("outcomeId", gap.outcomeId())
                .param("decisionId", gap.decisionId())
                .param("recommendationId", gap.recommendationId())
                .param("modelVersionId", gap.modelVersionId())
                .param("metricKey", gap.metricKey())
                .param("unit", gap.unit())
                .param("expectedValue", gap.expectedValue())
                .param("actualValue", gap.actualValue())
                .param("signedError", gap.signedError())
                .param("absoluteError", gap.absoluteError())
                .param("relativeError", gap.relativeError())
                .param("policyKey", gap.policyKey())
                .param("policyVersion", gap.policyVersion())
                .param("classification", gap.classification().name())
                .param("causeCategory", gap.causeCategory().name())
                .param("reviewStatus", gap.reviewStatus().name())
                .param("calibrationStatus", gap.calibrationStatus().name())
                .param("detectedAt", OffsetDateTime.ofInstant(gap.detectedAt(), ZoneOffset.UTC))
                .param("correlationId", correlationId)
                .update();
    }

    RealityGapView findById(UUID workspaceId, UUID gapId) {
        return queryBase("""
                 where workspace_id = :workspaceId
                   and id = :gapId
                """, workspaceId, null, gapId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Reality Gap not found"));
    }

    List<RealityGapView> findByOutcome(UUID workspaceId, UUID outcomeId) {
        return queryBase("""
                 where workspace_id = :workspaceId
                   and outcome_id = :outcomeId
                 order by metric_key
                """, workspaceId, outcomeId, null);
    }

    void insertReview(
            UUID reviewId,
            UUID workspaceId,
            UUID gapId,
            UUID reviewerActorId,
            ReviewRealityGapCommand command,
            List<UUID> evidenceIds,
            UUID correlationId,
            Instant reviewedAt
    ) {
        jdbc.sql("""
                insert into intelligence.error_hypothesis_review
                    (id, workspace_id, reality_gap_id, reviewer_actor_id, cause_category,
                     cause_confidence, rationale, review_status, calibration_status,
                     reviewed_at, correlation_id)
                values
                    (:id, :workspaceId, :gapId, :reviewerActorId, :causeCategory,
                     :causeConfidence, :rationale, :reviewStatus, :calibrationStatus,
                     :reviewedAt, :correlationId)
                """)
                .param("id", reviewId)
                .param("workspaceId", workspaceId)
                .param("gapId", gapId)
                .param("reviewerActorId", reviewerActorId)
                .param("causeCategory", command.causeCategory().name())
                .param("causeConfidence", command.causeConfidence())
                .param("rationale", command.rationale())
                .param("reviewStatus", command.reviewStatus().name())
                .param("calibrationStatus", command.calibrationStatus().name())
                .param("reviewedAt", OffsetDateTime.ofInstant(reviewedAt, ZoneOffset.UTC))
                .param("correlationId", correlationId)
                .update();

        for (UUID evidenceId : evidenceIds) {
            jdbc.sql("""
                    insert into intelligence.error_hypothesis_evidence(review_id, evidence_id)
                    values (:reviewId, :evidenceId)
                    """)
                    .param("reviewId", reviewId)
                    .param("evidenceId", evidenceId)
                    .update();
        }
    }

    int applyReview(UUID workspaceId, UUID gapId, ReviewRealityGapCommand command) {
        return jdbc.sql("""
                update intelligence.reality_gap
                   set cause_category = :causeCategory,
                       cause_confidence = :causeConfidence,
                       review_status = :reviewStatus,
                       calibration_status = :calibrationStatus
                 where id = :gapId
                   and workspace_id = :workspaceId
                   and review_status = 'PENDING_REVIEW'
                """)
                .param("workspaceId", workspaceId)
                .param("gapId", gapId)
                .param("causeCategory", command.causeCategory().name())
                .param("causeConfidence", command.causeConfidence())
                .param("reviewStatus", command.reviewStatus().name())
                .param("calibrationStatus", command.calibrationStatus().name())
                .update();
    }

    private List<RealityGapView> queryBase(
            String suffix,
            UUID workspaceId,
            UUID outcomeId,
            UUID gapId
    ) {
        var spec = jdbc.sql("""
                select id, outcome_id, decision_id, recommendation_id, model_version_id,
                       metric_key, unit, expected_value, actual_value, signed_error,
                       absolute_error, relative_error, policy_key, policy_version,
                       classification, cause_category, review_status, calibration_status,
                       detected_at
                  from intelligence.reality_gap
                """ + suffix)
                .param("workspaceId", workspaceId);
        if (outcomeId != null) {
            spec = spec.param("outcomeId", outcomeId);
        }
        if (gapId != null) {
            spec = spec.param("gapId", gapId);
        }
        return spec.query((rs, rowNum) -> new RealityGapView(
                rs.getObject("id", UUID.class),
                rs.getObject("outcome_id", UUID.class),
                rs.getObject("decision_id", UUID.class),
                rs.getObject("recommendation_id", UUID.class),
                rs.getObject("model_version_id", UUID.class),
                rs.getString("metric_key"),
                rs.getString("unit"),
                rs.getBigDecimal("expected_value"),
                rs.getBigDecimal("actual_value"),
                rs.getBigDecimal("signed_error"),
                rs.getBigDecimal("absolute_error"),
                rs.getBigDecimal("relative_error"),
                rs.getString("policy_key"),
                rs.getString("policy_version"),
                RealityGapClassification.valueOf(rs.getString("classification")),
                ErrorCauseCategory.valueOf(rs.getString("cause_category")),
                RealityGapReviewStatus.valueOf(rs.getString("review_status")),
                CalibrationStatus.valueOf(rs.getString("calibration_status")),
                rs.getObject("detected_at", OffsetDateTime.class).toInstant()
        )).list();
    }
}
