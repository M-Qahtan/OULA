package com.oula.intelligence;

import java.util.UUID;

public record DecisionTransactionContext(
        UUID decisionId,
        UUID workspaceId,
        UUID recommendationId,
        UUID intentId,
        UUID selectedPropertyId,
        boolean acceptedRecommendation,
        UUID modelVersionId
) {
}
