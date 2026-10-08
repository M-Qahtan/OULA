package com.oula.integration;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record InboundIntegrationReceipt(
        UUID id,
        UUID workspaceId,
        UUID partnerId,
        UUID contractId,
        String externalEventId,
        String operation,
        String resourceType,
        String purpose,
        Set<String> dataClasses,
        String payloadHash,
        String payloadReference,
        String authMode,
        String credentialReference,
        Instant authenticatedAt,
        Instant receivedAt,
        UUID correlationId,
        String status
) {
    public InboundIntegrationReceipt {
        dataClasses = Set.copyOf(dataClasses);
    }
}
