package com.oula.api;

import com.oula.intent.IntentDetails;
import com.oula.intent.IntentStatus;
import com.oula.intent.IntentType;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record IntentResponse(
        UUID id,
        UUID workspaceId,
        IntentType intentType,
        IntentStatus status,
        BigDecimal budgetMax,
        Integer minimumBedrooms,
        Set<String> preferredDistricts
) {
    static IntentResponse from(IntentDetails details) {
        return new IntentResponse(
                details.id(), details.workspaceId(), details.intentType(), details.status(),
                details.budgetMax(), details.minimumBedrooms(), details.preferredDistricts()
        );
    }
}
