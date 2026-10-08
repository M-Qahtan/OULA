package com.oula.integration;

import java.util.Set;
import java.util.UUID;

public record PrepareOutboundIntegrationCommand(
        String operation,
        String resourceType,
        UUID resourceId,
        String purpose,
        Set<String> dataClasses,
        String payloadHash,
        String payloadReference,
        UUID correlationId
) {
    public PrepareOutboundIntegrationCommand {
        dataClasses = dataClasses == null ? Set.of() : Set.copyOf(dataClasses);
    }
}
