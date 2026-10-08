package com.oula.integration;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record OutboundIntegrationRequest(
        UUID id,
        UUID workspaceId,
        UUID partnerId,
        UUID contractId,
        String operation,
        String resourceType,
        UUID resourceId,
        String purpose,
        Set<String> dataClasses,
        String payloadHash,
        String payloadReference,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID correlationId
) {
    public OutboundIntegrationRequest {
        dataClasses = Set.copyOf(dataClasses);
    }
}
