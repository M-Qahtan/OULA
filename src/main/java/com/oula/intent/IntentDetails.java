package com.oula.intent;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Workspace-scoped persisted intent projection; Person identity is modelled separately. */
public record IntentDetails(
        UUID id,
        UUID workspaceId,
        IntentType intentType,
        IntentStatus status,
        BigDecimal budgetMax,
        Integer minimumBedrooms,
        Set<String> preferredDistricts
) {
    public IntentDetails {
        preferredDistricts = Set.copyOf(preferredDistricts);
    }
}
