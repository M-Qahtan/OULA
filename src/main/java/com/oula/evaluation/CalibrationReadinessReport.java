package com.oula.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evidence-readiness report for scientific calibration review.
 * It is descriptive only: it does not validate model accuracy, establish
 * causality, authorize retraining or mutate model versions.
 */
public record CalibrationReadinessReport(
        UUID workspaceId,
        UUID modelVersionId,
        String protocolVersion,
        Instant evaluatedAt,
        String evidenceStage,
        long metricSeries,
        long observedGapSamples,
        long reviewedGaps,
        long calibrationCandidates,
        long unknownCauseGaps,
        BigDecimal reviewedCoverageOfSamples,
        BigDecimal candidateCoverageOfReviewed,
        List<MetricSignal> metrics,
        List<String> evidenceBlockers,
        List<String> validationRequirements,
        List<String> limitations,
        boolean automaticTrainingAllowed,
        boolean causalEffectClaimAllowed
) {
    public CalibrationReadinessReport {
        metrics = List.copyOf(metrics);
        evidenceBlockers = List.copyOf(evidenceBlockers);
        validationRequirements = List.copyOf(validationRequirements);
        limitations = List.copyOf(limitations);
    }

    public record MetricSignal(
            String metricKey,
            String policyKey,
            String policyVersion,
            long sampleCount,
            BigDecimal averageAbsoluteError,
            BigDecimal averageRelativeError,
            long materialVarianceCount,
            long severeVarianceCount,
            long reviewedCount,
            long calibrationCandidateCount,
            long unknownCauseCount,
            Instant lastDetectedAt
    ) {}
}
