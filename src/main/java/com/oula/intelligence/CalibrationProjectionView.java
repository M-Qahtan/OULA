package com.oula.intelligence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CalibrationProjectionView(
        UUID modelVersionId,
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
) {
}
