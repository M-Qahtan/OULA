package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
                     signed_error, absolute_error, relative_error, cause_category,
                     review_status, calibration_status, detected_at, correlation_id)
                values
                    (:id, :workspaceId, :outcomeId, :decisionId, :recommendationId,
                     :modelVersionId, :metricKey, :unit, :expectedValue, :actualValue,
                     :signedError, :absoluteError, :relativeError, :causeCategory,
                     :reviewStatus, :calibrationStatus, :detectedAt, :correlationId)
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
                .param("causeCategory", gap.causeCategory().name())
                .param("reviewStatus", gap.reviewStatus().name())
                .param("calibrationStatus", gap.calibrationStatus().name())
                .param("detectedAt", OffsetDateTime.ofInstant(gap.detectedAt(), ZoneOffset.UTC))
                .param("correlationId", correlationId)
                .update();
    }

    List<RealityGapView> findByOutcome(UUID workspaceId, UUID outcomeId) {
        return jdbc.sql("""
                select id, outcome_id, decision_id, recommendation_id, model_version_id,
                       metric_key, unit, expected_value, actual_value, signed_error,
                       absolute_error, relative_error, cause_category, review_status,
                       calibration_status, detected_at
                  from intelligence.reality_gap
                 where workspace_id = :workspaceId
                   and outcome_id = :outcomeId
                 order by metric_key
                """)
                .param("workspaceId", workspaceId)
                .param("outcomeId", outcomeId)
                .query((rs, rowNum) -> new RealityGapView(
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
                        ErrorCauseCategory.valueOf(rs.getString("cause_category")),
                        RealityGapReviewStatus.valueOf(rs.getString("review_status")),
                        CalibrationStatus.valueOf(rs.getString("calibration_status")),
                        rs.getObject("detected_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }
}
