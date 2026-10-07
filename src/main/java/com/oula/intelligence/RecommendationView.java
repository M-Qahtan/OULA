package com.oula.intelligence;

import java.util.List;
import java.util.UUID;

public record RecommendationView(
        UUID recommendationId,
        UUID matchRunId,
        UUID recommendedPropertyId,
        double lifeFitScore,
        double confidence,
        String modelId,
        String modelVersion,
        List<UUID> evidenceIds,
        List<UUID> assumptionIds
) {
    public RecommendationView {
        evidenceIds = List.copyOf(evidenceIds);
        assumptionIds = List.copyOf(assumptionIds);
    }
}
