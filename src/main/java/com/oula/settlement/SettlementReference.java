package com.oula.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementReference(
        UUID id,
        UUID workspaceId,
        UUID workOrderId,
        UUID providerPartyId,
        String processorCode,
        String externalReference,
        BigDecimal amount,
        String currency,
        String status,
        UUID evidenceId,
        UUID recordedBy,
        Instant recordedAt
) {}
