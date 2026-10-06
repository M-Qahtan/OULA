package com.oula.intelligence;

import java.util.UUID;

public record DecisionView(
        UUID decisionId,
        UUID recommendationId,
        UUID selectedPropertyId,
        boolean acceptedRecommendation
) {
}
