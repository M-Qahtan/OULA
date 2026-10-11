package com.oula.evaluation;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.intelligence.CalibrationProjectionView;
import com.oula.intelligence.RealityMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalibrationReadinessServiceTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID model = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-10T07:00:00Z");
    private final AccessContext access = new AccessContext(
            actor, "calibration-readiness-test", workspace,
            AccessPurpose.PROPERTY_DECISION_SUPPORT);

    private RealityMemoryService memory;
    private CalibrationReadinessService service;

    @BeforeEach
    void setup() {
        memory = mock(RealityMemoryService.class);
        service = new CalibrationReadinessService(
                memory, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void aggregatesObservedSignalsWithoutClaimingValidationOrCausality() {
        when(memory.calibration(access, model)).thenReturn(List.of(
                projection("commuteMinutes", 10, 8, 3, 2),
                projection("monthlyCost", 6, 4, 1, 1)));

        var result = service.evaluate(access, model);

        assertThat(result.observedGapSamples()).isEqualTo(16);
        assertThat(result.reviewedGaps()).isEqualTo(12);
        assertThat(result.calibrationCandidates()).isEqualTo(4);
        assertThat(result.unknownCauseGaps()).isEqualTo(3);
        assertThat(result.reviewedCoverageOfSamples()).isEqualByComparingTo("0.750000");
        assertThat(result.candidateCoverageOfReviewed()).isEqualByComparingTo("0.333333");
        assertThat(result.evidenceStage())
                .isEqualTo("EVIDENCE_BACKED_CALIBRATION_CANDIDATES_PRESENT");
        assertThat(result.evidenceBlockers()).contains(
                "UNREVIEWED_GAPS_PRESENT", "UNKNOWN_CAUSE_GAPS_PRESENT");
        assertThat(result.automaticTrainingAllowed()).isFalse();
        assertThat(result.causalEffectClaimAllowed()).isFalse();
        assertThat(result.validationRequirements()).contains(
                "DEFINE_ELIGIBLE_CASE_DENOMINATOR_AND_COHORT",
                "REQUIRE_SEPARATE_VALIDATION_AND_APPROVAL_BEFORE_MODEL_CHANGE");
    }

    @Test
    void zeroSamplesRemainUndefinedRatherThanFalseZeroEffectiveness() {
        when(memory.calibration(access, model)).thenReturn(List.of());

        var result = service.evaluate(access, model);

        assertThat(result.evidenceStage()).isEqualTo("NO_OBSERVED_REALITY_GAPS");
        assertThat(result.reviewedCoverageOfSamples()).isNull();
        assertThat(result.candidateCoverageOfReviewed()).isNull();
        assertThat(result.evidenceBlockers()).contains(
                "NO_REALITY_GAP_SAMPLES", "NO_CALIBRATION_CANDIDATES");
    }

    @Test
    void rejectsIncorrectPurposeBeforeReadingScientificEvidence() {
        AccessContext wrong = new AccessContext(
                actor, "test", workspace, AccessPurpose.PROPERTY_MANAGEMENT);

        assertThatThrownBy(() -> service.evaluate(wrong, model))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(memory);
    }

    @Test
    void rejectsCrossModelProjectionContamination() {
        when(memory.calibration(access, model)).thenReturn(List.of(
                new CalibrationProjectionView(
                        UUID.randomUUID(), "commuteMinutes", "commuteMinutes", "v1",
                        1, new BigDecimal("1.0"), new BigDecimal("0.1"),
                        0, 0, 1, 1, 0, now)));

        assertThatThrownBy(() -> service.evaluate(access, model))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("model mismatch");
    }

    private CalibrationProjectionView projection(
            String metric, long samples, long reviewed, long candidates, long unknown) {
        return new CalibrationProjectionView(
                model, metric, metric, "v1", samples,
                new BigDecimal("2.500000"),
                new BigDecimal("0.125000"),
                2, 1, reviewed, candidates, unknown, now.minusSeconds(60));
    }
}
