package com.oula.intelligence;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RealityGapReviewIntegrationTest {
    private static final UUID MODEL_VERSION_ID =
            UUID.fromString("00000000-0000-8000-8000-000000000001");

    @Autowired RealityGapReviewService reviews;
    @Autowired RealityMemoryService memory;
    @Autowired JdbcTemplate jdbc;

    @Test
    void promotesOnlyEvidenceBackedReviewedHypothesisToCalibrationCandidate() {
        Seed seed = seedCase();
        AccessContext access = new AccessContext(
                seed.actorId(),
                "review-test",
                seed.workspaceId(),
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );

        RealityGapView reviewed = reviews.review(
                access,
                seed.gapId(),
                new ReviewRealityGapCommand(
                        ErrorCauseCategory.MODEL_ERROR,
                        0.80,
                        "Reviewed against verified post-decision evidence.",
                        RealityGapReviewStatus.REVIEWED,
                        CalibrationStatus.CANDIDATE,
                        List.of(seed.evidenceId())
                ),
                UUID.randomUUID()
        );

        assertThat(reviewed.reviewStatus()).isEqualTo(RealityGapReviewStatus.REVIEWED);
        assertThat(reviewed.calibrationStatus()).isEqualTo(CalibrationStatus.CANDIDATE);
        assertThat(reviewed.causeCategory()).isEqualTo(ErrorCauseCategory.MODEL_ERROR);
        assertThat(jdbc.queryForObject(
                "select count(*) from intelligence.error_hypothesis_review where reality_gap_id = ?",
                Long.class,
                seed.gapId()
        )).isEqualTo(1L);

        RealityCaseView realityCase = memory.realityCase(access, seed.outcomeId());
        assertThat(realityCase.propertyId()).isEqualTo(seed.propertyId());
        assertThat(realityCase.realityGapCount()).isEqualTo(1);
        assertThat(realityCase.pendingReviewCount()).isZero();

        List<CalibrationProjectionView> projection = memory.calibration(
                access,
                MODEL_VERSION_ID
        );
        assertThat(projection).hasSize(1);
        assertThat(projection.getFirst().calibrationCandidateCount()).isEqualTo(1);
    }

    private Seed seedCase() {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        UUID outcomeId = UUID.randomUUID();
        UUID gapId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Reality Test", "ACTIVE"
        );
        jdbc.update(
                "insert into intent.intent(id, workspace_id, intent_type, status) values (?,?,?,?)",
                intentId, workspaceId, "BUY", "ACTIVE"
        );
        jdbc.update(
                """
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Riyadh", 4, 2_000_000
        );
        jdbc.update(
                """
                insert into intelligence.recommendation
                    (id, workspace_id, intent_id, recommended_property_id, alternatives,
                     model_id, model_version, confidence, generated_at, correlation_id,
                     model_version_id, status)
                values (?, ?, ?, ?, '[]'::jsonb, ?, ?, ?, now(), ?, ?, ?)
                """,
                recommendationId, workspaceId, intentId, propertyId,
                "LifeFit", "v1", 0.90, correlationId, MODEL_VERSION_ID, "GENERATED"
        );
        jdbc.update(
                """
                insert into intelligence.decision_record
                    (id, workspace_id, recommendation_id, selected_property_id,
                     decision_maker, accepted_recommendation, decided_at)
                values (?,?,?,?,?,?,now())
                """,
                decisionId, workspaceId, recommendationId, propertyId, actorId, true
        );
        jdbc.update(
                """
                insert into intelligence.outcome
                    (id, workspace_id, decision_id, outcome_type, expected_json,
                     actual_json, observed_at)
                values (?, ?, ?, ?, '{}'::jsonb, '{}'::jsonb, now())
                """,
                outcomeId, workspaceId, decisionId, "POST_DECISION_FEEDBACK"
        );
        jdbc.update(
                """
                insert into intelligence.evidence
                    (id, workspace_id, subject_type, subject_id, evidence_type,
                     source_type, source_identity, captured_at, content_hash,
                     verification_status, sensitivity)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """,
                evidenceId, workspaceId, "OUTCOME", outcomeId, "INSPECTION",
                "POST_DECISION_REVIEW", "review-1", Instant.now(),
                "sha256:review-evidence", "VERIFIED", "INTERNAL"
        );
        jdbc.update(
                """
                insert into intelligence.reality_gap
                    (id, workspace_id, outcome_id, decision_id, recommendation_id,
                     model_version_id, metric_key, unit, expected_value, actual_value,
                     signed_error, absolute_error, relative_error, cause_category,
                     review_status, calibration_status, detected_at, correlation_id,
                     policy_key, policy_version, classification)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now(),?,?,?,?)
                """,
                gapId, workspaceId, outcomeId, decisionId, recommendationId,
                MODEL_VERSION_ID, "commuteMinutes", "MINUTES",
                22, 27, -5, 5, 0.22727273, "UNKNOWN_CAUSE",
                "PENDING_REVIEW", "UNASSESSED", correlationId,
                "commuteMinutes", "v1", "MATERIAL_VARIANCE"
        );

        return new Seed(
                workspaceId, actorId, propertyId, outcomeId, gapId, evidenceId
        );
    }

    private record Seed(
            UUID workspaceId,
            UUID actorId,
            UUID propertyId,
            UUID outcomeId,
            UUID gapId,
            UUID evidenceId
    ) {
    }
}
