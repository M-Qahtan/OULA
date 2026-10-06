package com.oula.intelligence;

import java.util.Map;
import java.util.UUID;

public record OutcomeView(
        UUID outcomeId,
        UUID decisionId,
        UUID observationId,
        Map<String, Object> expectedMetrics,
        Map<String, Object> actualMetrics,
        Map<String, Object> varianceMetrics
) {
    public OutcomeView {
        expectedMetrics = Map.copyOf(expectedMetrics);
        actualMetrics = Map.copyOf(actualMetrics);
        varianceMetrics = Map.copyOf(varianceMetrics);
    }
}
