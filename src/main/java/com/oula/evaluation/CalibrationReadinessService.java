package com.oula.evaluation;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.intelligence.CalibrationProjectionView;
import com.oula.intelligence.RealityMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds an evidence-readiness view from existing Reality Gap calibration
 * projections. No thresholds are treated as proof of validity or causal effect.
 */
@Service
public class CalibrationReadinessService {
    public static final String PROTOCOL_VERSION = "calibration-readiness-v1";

    private final RealityMemoryService memory;
    private final Clock clock;

    @Autowired
    public CalibrationReadinessService(RealityMemoryService memory) {
        this(memory, Clock.systemUTC());
    }

    CalibrationReadinessService(RealityMemoryService memory, Clock clock) {
        this.memory = memory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalibrationReadinessReport evaluate(AccessContext access, UUID modelVersionId) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(modelVersionId, "modelVersionId");
        if (access.purpose() != AccessPurpose.PROPERTY_DECISION_SUPPORT) {
            throw new SecurityException("PROPERTY_DECISION_SUPPORT purpose required");
        }

        List<CalibrationProjectionView> projections = memory.calibration(access, modelVersionId);
        if (projections.stream().anyMatch(p -> !modelVersionId.equals(p.modelVersionId()))) {
            throw new SecurityException("calibration projection model mismatch");
        }

        long samples = projections.stream().mapToLong(CalibrationProjectionView::sampleCount).sum();
        long reviewed = projections.stream().mapToLong(CalibrationProjectionView::reviewedCount).sum();
        long candidates = projections.stream().mapToLong(CalibrationProjectionView::calibrationCandidateCount).sum();
        long unknown = projections.stream().mapToLong(CalibrationProjectionView::unknownCauseCount).sum();

        List<String> blockers = new ArrayList<>();
        if (samples == 0) blockers.add("NO_REALITY_GAP_SAMPLES");
        if (reviewed < samples) blockers.add("UNREVIEWED_GAPS_PRESENT");
        if (unknown > 0) blockers.add("UNKNOWN_CAUSE_GAPS_PRESENT");
        if (candidates == 0) blockers.add("NO_CALIBRATION_CANDIDATES");

        String stage;
        if (samples == 0) {
            stage = "NO_OBSERVED_REALITY_GAPS";
        } else if (reviewed == 0) {
            stage = "OBSERVATIONAL_SIGNAL_UNREVIEWED";
        } else if (candidates == 0) {
            stage = "REVIEWED_SIGNAL_NO_CALIBRATION_CANDIDATE";
        } else {
            stage = "EVIDENCE_BACKED_CALIBRATION_CANDIDATES_PRESENT";
        }

        List<CalibrationReadinessReport.MetricSignal> metrics = projections.stream()
                .map(p -> new CalibrationReadinessReport.MetricSignal(
                        p.metricKey(), p.policyKey(), p.policyVersion(),
                        p.sampleCount(), p.averageAbsoluteError(), p.averageRelativeError(),
                        p.materialVarianceCount(), p.severeVarianceCount(), p.reviewedCount(),
                        p.calibrationCandidateCount(), p.unknownCauseCount(), p.lastDetectedAt()))
                .toList();

        return new CalibrationReadinessReport(
                access.workspaceId(),
                modelVersionId,
                PROTOCOL_VERSION,
                clock.instant(),
                stage,
                projections.size(),
                samples,
                reviewed,
                candidates,
                unknown,
                ratio(reviewed, samples),
                ratio(candidates, reviewed),
                metrics,
                blockers,
                List.of(
                        "DEFINE_ELIGIBLE_CASE_DENOMINATOR_AND_COHORT",
                        "PRE_REGISTER_OUTCOME_METRIC_TIME_HORIZON_AND_EXCLUSIONS",
                        "MEASURE_BASELINE_AND_DATA_COMPLETENESS",
                        "ESTABLISH_APPROPRIATE_COMPARISON_GROUP_AND_CONFOUNDERS",
                        "INDEPENDENTLY_VERIFY_OUTCOMES_AND_PROVENANCE",
                        "PERFORM_QUALIFIED_STATISTICAL_AND_CAUSAL_REVIEW_BEFORE_EFFECT_CLAIM",
                        "REQUIRE_SEPARATE_VALIDATION_AND_APPROVAL_BEFORE_MODEL_CHANGE"),
                List.of(
                        "OBSERVATIONAL_REALITY_GAPS_DO_NOT_ESTABLISH_CAUSAL_EFFECT",
                        "CALIBRATION_CANDIDATE_DOES_NOT_MEAN_MODEL_UPDATE_IS_APPROVED",
                        "AVERAGE_ERROR_WITHOUT_COHORT_AND_TIME_HORIZON_IS_DESCRIPTIVE_ONLY",
                        "UNKNOWN_CAUSE_MUST_REMAIN_EXPLICIT",
                        "CROSS_WORKSPACE_POOLING_IS_NOT_PERMITTED_BY_THIS REPORT"),
                false,
                false);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
