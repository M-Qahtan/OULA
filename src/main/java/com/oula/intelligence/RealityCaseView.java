package com.oula.intelligence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RealityCaseView(
        UUID outcomeId,
        UUID decisionId,
        UUID recommendationId,
        UUID propertyId,
        UUID propertySnapshotId,
        UUID modelVersionId,
        Instant recommendationGeneratedAt,
        Instant decidedAt,
        Instant observedAt,
        long realityGapCount,
        long pendingReviewCount,
        BigDecimal maxAbsoluteError,
        BigDecimal maxRelativeError
) {
}
