package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class RealityMemoryRepository {
    private final JdbcClient jdbc;

    RealityMemoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    RealityCaseView realityCase(UUID workspaceId, UUID outcomeId) {
        return jdbc.sql("""
                select outcome_id, decision_id, recommendation_id, property_id,
                       property_snapshot_id, model_version_id, recommendation_generated_at,
                       decided_at, observed_at, reality_gap_count, pending_review_count,
                       max_absolute_error, max_relative_error
                  from intelligence.v_reality_case
                 where workspace_id = :workspaceId
                   and outcome_id = :outcomeId
                """)
                .param("workspaceId", workspaceId)
                .param("outcomeId", outcomeId)
                .query((rs, rowNum) -> new RealityCaseView(
                        rs.getObject("outcome_id", UUID.class),
                        rs.getObject("decision_id", UUID.class),
                        rs.getObject("recommendation_id", UUID.class),
                        rs.getObject("property_id", UUID.class),
                        rs.getObject("property_snapshot_id", UUID.class),
                        rs.getObject("model_version_id", UUID.class),
                        rs.getObject("recommendation_generated_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("observed_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("reality_gap_count"),
                        rs.getLong("pending_review_count"),
                        rs.getBigDecimal("max_absolute_error"),
                        rs.getBigDecimal("max_relative_error")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("reality case not found"));
    }

    List<CalibrationProjectionView> calibration(UUID workspaceId, UUID modelVersionId) {
        return jdbc.sql("""
                select model_version_id, metric_key, policy_key, policy_version,
                       sample_count, avg_absolute_error, avg_relative_error,
                       material_variance_count, severe_variance_count, reviewed_count,
                       calibration_candidate_count, unknown_cause_count, last_detected_at
                  from intelligence.v_model_calibration_signal
                 where workspace_id = :workspaceId
                   and model_version_id = :modelVersionId
                 order by metric_key, policy_version
                """)
                .param("workspaceId", workspaceId)
                .param("modelVersionId", modelVersionId)
                .query((rs, rowNum) -> new CalibrationProjectionView(
                        rs.getObject("model_version_id", UUID.class),
                        rs.getString("metric_key"),
                        rs.getString("policy_key"),
                        rs.getString("policy_version"),
                        rs.getLong("sample_count"),
                        rs.getBigDecimal("avg_absolute_error"),
                        rs.getBigDecimal("avg_relative_error"),
                        rs.getLong("material_variance_count"),
                        rs.getLong("severe_variance_count"),
                        rs.getLong("reviewed_count"),
                        rs.getLong("calibration_candidate_count"),
                        rs.getLong("unknown_cause_count"),
                        rs.getObject("last_detected_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }
}
