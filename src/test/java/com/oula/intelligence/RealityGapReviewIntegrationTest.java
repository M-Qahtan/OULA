package com.oula.intelligence;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThatThrownBy(() -> reviews.review(
                access,
                seed.gapId(),
                new ReviewRealityGapCommand(
                        ErrorCauseCategory.MODEL_ERROR,
                        0.80,
                        "A second review must not overwrite the first.",
                        RealityGapReviewStatus.REVIEWED,
                        CalibrationStatus.CANDIDATE,
                        List.of(seed.evidenceId())
                ),
                UUID.randomUUID()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");

        assertThat(jdbc.queryForObject(
                "select count(*) from intelligence.error_hypothesis_review where reality_gap_id = ?",
                Long.class,
                seed.gapId()
        )).isEqualTo(1L);

        RealityCaseView realityCase = memory.realityCase(access, seed.outcomeId());
        assertThat(realityCase.propertyId()).isEqualTo(seed.propertyId());
        assertThat(realityCase.realityGapCount()).isEqualTo(1);
        assertThat(realityCase.pendingReviewCount()).isZero();

        RealityTimelineView timeline = memory.timeline(access, seed.outcomeId());
        assertThat(timeline.realityCase().outcomeId()).isEqualTo(seed.outcomeId());
        assertThat(timeline.events())
                .extracting(RealityTimelineEvent::kind)
                .containsExactly(
                        "RECOMMENDATION",
                        "HUMAN_DECISION",
                        "OUTCOME",
                        "REALITY_GAP",
                        "HUMAN_GAP_REVIEW"
                );
        assertThat(timeline.events().getLast().epistemicClass())
                .isEqualTo("REVIEWED_HYPOTHESIS");

        List<CalibrationProjectionView> projection = memory.calibration(
                access,
                MODEL_VERSION_ID
        );
        assertThat(projection).hasSize(1);
        assertThat(projection.getFirst().calibrationCandidateCount()).isEqualTo(1);
    }

    @Test
    void timelinePreservesDecisionTimeKnowledgeAndWorkspaceIsolation() {
        Seed seed = seedCase();
        AccessContext access = new AccessContext(
                seed.actorId(), "timeline-test", seed.workspaceId(),
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );
        OffsetDateTime decidedAt = jdbc.queryForObject(
                "select decided_at from intelligence.decision_record where id = ?",
                OffsetDateTime.class,
                jdbcQueryId(seed.outcomeId())
        );
        UUID knownSnapshot = UUID.randomUUID();
        UUID lateSnapshot = UUID.randomUUID();
        OffsetDateTime historicalTime = decidedAt.minusMinutes(10);

        jdbc.update(
                """
                insert into property.state_snapshot(
                    id, workspace_id, property_id, version, effective_at, recorded_at,
                    state_basis, state_json, source_type, evidence_refs, correlation_id
                ) values (?, ?, ?, 1, ?, ?, 'UNKNOWN', '{}'::jsonb,
                          'HISTORICAL_CONTEXT', '[]'::jsonb, ?)
                """,
                knownSnapshot, seed.workspaceId(), seed.propertyId(),
                historicalTime, historicalTime, UUID.randomUUID()
        );
        jdbc.update(
                """
                insert into property.state_snapshot(
                    id, workspace_id, property_id, version, effective_at, recorded_at,
                    state_basis, state_json, source_type, evidence_refs, correlation_id,
                    supersedes_snapshot_id
                ) values (?, ?, ?, 2, ?, ?, 'UNKNOWN', '{}'::jsonb,
                          'LATE_INFORMATION', '[]'::jsonb, ?, ?)
                """,
                lateSnapshot, seed.workspaceId(), seed.propertyId(),
                historicalTime, decidedAt.plusSeconds(60), UUID.randomUUID(),
                knownSnapshot
        );

        RealityTimelineView timeline = memory.timeline(access, seed.outcomeId());
        assertThat(timeline.realityCase().propertySnapshotId()).isEqualTo(knownSnapshot);
        assertThat(timeline.events()).extracting(RealityTimelineEvent::sourceId)
                .contains(knownSnapshot)
                .doesNotContain(lateSnapshot);
        assertThat(timeline.events().getFirst().kind()).isEqualTo("PROPERTY_STATE_KNOWN");
        assertThat(timeline.events().getFirst().status()).isEqualTo("UNKNOWN");
        assertThat(timeline.events()).extracting(RealityTimelineEvent::occurredAt)
                .isSorted();

        AccessContext foreignAccess = new AccessContext(
                UUID.randomUUID(), "foreign-test", UUID.randomUUID(),
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );
        assertThatThrownBy(() -> memory.timeline(foreignAccess, seed.outcomeId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private UUID jdbcQueryId(UUID outcomeId) {
        return jdbc.queryForObject(
                "select decision_id from intelligence.outcome where id = ?",
                UUID.class, outcomeId
        );
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
                "POST_DECISION_REVIEW", "review-1", OffsetDateTime.now(ZoneOffset.UTC),
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
