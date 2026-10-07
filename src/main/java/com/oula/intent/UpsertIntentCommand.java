package com.oula.intent;

import java.math.BigDecimal;
import java.util.Set;

public record UpsertIntentCommand(
        String intentType,
        BigDecimal budgetMax,
        Integer minimumBedrooms,
        Set<String> preferredDistricts
) {
}
