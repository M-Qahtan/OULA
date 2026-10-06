package com.oula.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecordOutcomeCommand(
        UUID decisionId,
        int actualCommuteMinutes,
        double satisfactionScore,
        double confidence,
        List<UUID> evidenceIds,
        Instant observedAt
) {
    public RecordOutcomeCommand {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
