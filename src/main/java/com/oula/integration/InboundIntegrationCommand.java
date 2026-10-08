package com.oula.integration;

import java.util.Set;
import java.util.UUID;

public record InboundIntegrationCommand(
        String externalEventId,
        String operation,
        String resourceType,
        String purpose,
        Set<String> dataClasses,
        String payloadHash,
        String payloadReference,
        UUID correlationId
) {
    public InboundIntegrationCommand {
        dataClasses = dataClasses == null ? Set.of() : Set.copyOf(dataClasses);
    }
}
