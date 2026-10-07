package com.oula.matching;

import java.util.List;
import java.util.UUID;

public record MatchRunDecisionView(
        UUID matchRunId,
        UUID workspaceId,
        UUID intentId,
        String algorithmVersion,
        List<MatchAlternativeDecisionView> alternatives
) {
    public MatchRunDecisionView {
        alternatives = List.copyOf(alternatives);
    }

    public MatchAlternativeDecisionView recommended() {
        return alternatives.stream()
                .filter(alternative -> alternative.rank() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("completed match run has no rank-1 result"));
    }
}
