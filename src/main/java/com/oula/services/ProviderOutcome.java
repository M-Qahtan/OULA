package com.oula.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProviderOutcome(
        UUID id,
        UUID workspaceId,
        UUID workOrderId,
        UUID quoteId,
        UUID providerId,
        BigDecimal quotedAmount,
        BigDecimal actualCost,
        BigDecimal costVariance,
        Integer rating,
        String outcomeNote,
        UUID recordedBy,
        Instant recordedAt
) {}
