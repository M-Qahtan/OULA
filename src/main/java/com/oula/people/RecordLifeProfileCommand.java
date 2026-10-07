package com.oula.people;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record RecordLifeProfileCommand(
        UUID householdId,
        int householdSize,
        BigDecimal maxHousingBudget,
        Set<UUID> mobilityAnchors,
        Map<String, Object> preferences,
        Map<String, Object> constraints,
        Instant effectiveAt
) {
}
