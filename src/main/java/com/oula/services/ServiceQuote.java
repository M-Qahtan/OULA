package com.oula.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceQuote(
        UUID id,
        UUID workspaceId,
        UUID workOrderId,
        UUID providerId,
        UUID providerPartyId,
        BigDecimal amount,
        String currency,
        int leadTimeDays,
        String scopeNote,
        String status,
        Instant submittedAt,
        UUID selectedBy,
        Instant selectedAt,
        long version
) {}
