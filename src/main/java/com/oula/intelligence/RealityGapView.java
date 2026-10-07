package com.oula.intelligence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RealityGapView(
        UUID gapId,
        UUID outcomeId,
        UUID decisionId,
        UUID recommendationId,
        UUID modelVersionId,
        String metricKey,
        String unit,
        BigDecimal expectedValue,
        BigDecimal actualValue,
        BigDecimal signedError,
        BigDecimal absoluteError,
        BigDecimal relativeError,
        String policyKey,
        String policyVersion,
        RealityGapClassification classification,
        ErrorCauseCategory causeCategory,
        RealityGapReviewStatus reviewStatus,
        CalibrationStatus calibrationStatus,
        Instant detectedAt
) {
}
