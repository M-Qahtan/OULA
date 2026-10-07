package com.oula.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record IntentView(
        UUID intentId,
        UUID workspaceId,
        String intentType,
        IntentStatus status,
        BigDecimal budgetMax,
        Integer minimumBedrooms,
        Set<String> preferredDistricts,
        long version,
        Instant activatedAt
) {
}
