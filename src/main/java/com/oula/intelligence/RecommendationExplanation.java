package com.oula.intelligence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecommendationExplanation(
        UUID recommendationId,
        String executiveSummary,
        UUID recommendedPropertyId,
        double lifeFitScore,
        double confidence,
        Map<String, Object> confidenceBreakdown,
        Map<String, Object> uncertainty,
        String modelId,
        String modelVersion,
        String modelType,
        String riskClass,
        List<UUID> evidenceIds,
        List<UUID> assumptionIds,
        List<AlternativeExplanation> alternatives
) {
    public RecommendationExplanation {
        confidenceBreakdown = Map.copyOf(confidenceBreakdown);
        uncertainty = Map.copyOf(uncertainty);
        evidenceIds = List.copyOf(evidenceIds);
        assumptionIds = List.copyOf(assumptionIds);
        alternatives = List.copyOf(alternatives);
    }

    public record AlternativeExplanation(
            UUID propertyId,
            int rank,
            double lifeFitScore,
            double confidence,
            Map<String, Object> dimensions,
            Integer expectedCommuteMinutes
    ) {
        public AlternativeExplanation {
            dimensions = Map.copyOf(dimensions);
        }
    }
}
