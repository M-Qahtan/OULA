package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads existing canonical records; does not persist a parallel event ledger.
 * The RealityCase view is the tenant-scoped, anti-hindsight anchor.
 */
@Repository
class RealityTimelineRepository {
    private final JdbcClient jdbc;

    RealityTimelineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<RealityTimelineEvent> forOutcome(UUID workspaceId, UUID outcomeId) {
        return jdbc.sql("""
                WITH scoped_case AS (
                    SELECT *
                      FROM intelligence.v_reality_case
                     WHERE workspace_id = :workspaceId
                       AND outcome_id = :outcomeId
                ), events AS (
                    SELECT 'PROPERTY_STATE_KNOWN' AS kind, s.id AS source_id,
                           s.recorded_at AS occurred_at, 'RECORDED_STATE' AS epistemic_class,
                           s.state_basis AS status, 0 AS stage_order
                      FROM scoped_case c
                      JOIN property.state_snapshot s
                        ON s.id = c.property_snapshot_id
                       AND s.workspace_id = c.workspace_id
                       AND s.property_id = c.property_id
                     WHERE s.recorded_at <= c.decided_at
                       AND s.effective_at <= c.decided_at
                    UNION ALL
                    SELECT 'RECOMMENDATION', r.id, r.generated_at,
                           'MODEL_PROPOSAL', r.status, 1
                      FROM scoped_case c
                      JOIN intelligence.recommendation r
                        ON r.id = c.recommendation_id AND r.workspace_id = c.workspace_id
                    UNION ALL
                    SELECT 'HUMAN_DECISION', d.id, d.decided_at,
                           'HUMAN_CHOICE',
                           CASE WHEN d.accepted_recommendation
                               THEN 'RECOMMENDATION_ACCEPTED' ELSE 'OVERRIDE_RECORDED' END, 2
                      FROM scoped_case c
                      JOIN intelligence.decision_record d
                        ON d.id = c.decision_id AND d.workspace_id = c.workspace_id
                    UNION ALL
                    SELECT 'OUTCOME', o.id, o.observed_at,
                           'RECORDED_OUTCOME', o.outcome_type, 3
                      FROM scoped_case c
                      JOIN intelligence.outcome o
                        ON o.id = c.outcome_id AND o.workspace_id = c.workspace_id
                    UNION ALL
                    SELECT 'REALITY_GAP', g.id, g.detected_at,
                           'MEASURED_VARIANCE', g.classification, 4
                      FROM scoped_case c
                      JOIN intelligence.reality_gap g
                        ON g.outcome_id = c.outcome_id AND g.workspace_id = c.workspace_id
                    UNION ALL
                    SELECT 'HUMAN_GAP_REVIEW', review.id, review.reviewed_at,
                           'REVIEWED_HYPOTHESIS', review.review_status, 5
                      FROM scoped_case c
                      JOIN intelligence.reality_gap g
                        ON g.outcome_id = c.outcome_id AND g.workspace_id = c.workspace_id
                      JOIN intelligence.error_hypothesis_review review
                        ON review.reality_gap_id = g.id AND review.workspace_id = c.workspace_id
                )
                SELECT kind, source_id, occurred_at, epistemic_class, status
                  FROM events
                 ORDER BY occurred_at ASC, stage_order ASC, source_id ASC
                """)
                .param("workspaceId", workspaceId)
                .param("outcomeId", outcomeId)
                .query((rs, index) -> new RealityTimelineEvent(
                        rs.getString("kind"),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getString("epistemic_class"),
                        rs.getString("status")
                ))
                .list();
    }
}
