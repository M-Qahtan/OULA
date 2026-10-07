package com.oula.intelligence;

import java.util.List;
import java.util.UUID;

public record GenerateRecommendationCommand(
        UUID matchRunId,
        List<UUID> evidenceIds,
        List<UUID> assumptionIds,
        int validHours
) {
    public GenerateRecommendationCommand {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        assumptionIds = assumptionIds == null ? List.of() : List.copyOf(assumptionIds);
    }
}
