package com.oula.matching;

import java.util.UUID;

public record MatchAlternativeDecisionView(
        UUID propertyId,
        int rank,
        double lifeFitScore,
        double confidence,
        String explanationJson,
        Integer expectedCommuteMinutes
) {
}
