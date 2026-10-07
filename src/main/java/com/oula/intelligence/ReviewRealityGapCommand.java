package com.oula.intelligence;

import java.util.List;
import java.util.UUID;

public record ReviewRealityGapCommand(
        ErrorCauseCategory causeCategory,
        Double causeConfidence,
        String rationale,
        RealityGapReviewStatus reviewStatus,
        CalibrationStatus calibrationStatus,
        List<UUID> evidenceIds
) {
}
