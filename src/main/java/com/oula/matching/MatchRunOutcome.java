package com.oula.matching;

import java.util.List;
import java.util.UUID;

public record MatchRunOutcome(UUID matchRunId, List<RankedMatch> matches) {
    public MatchRunOutcome {
        matches = List.copyOf(matches);
    }
}
