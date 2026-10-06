package com.oula.matching;

import java.util.Map;
import java.util.UUID;

public record RankedMatch(
        UUID propertyId,
        int rank,
        double score,
        double confidence,
        Map<String, Double> dimensions
) {
    public RankedMatch {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
        dimensions = Map.copyOf(dimensions);
    }
}
