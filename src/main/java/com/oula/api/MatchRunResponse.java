package com.oula.api;

import com.oula.matching.MatchRunOutcome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchRunResponse(UUID matchRunId, List<MatchResponseItem> matches) {

    static MatchRunResponse from(MatchRunOutcome outcome) {
        return new MatchRunResponse(
                outcome.matchRunId(),
                outcome.matches().stream()
                        .map(match -> new MatchResponseItem(
                                match.propertyId(),
                                match.rank(),
                                match.score(),
                                match.confidence(),
                                match.dimensions()
                        ))
                        .toList()
        );
    }

    public record MatchResponseItem(
            UUID propertyId,
            int rank,
            double score,
            double confidence,
            Map<String, Double> dimensions
    ) {
        public MatchResponseItem {
            dimensions = Map.copyOf(dimensions);
        }
    }
}
