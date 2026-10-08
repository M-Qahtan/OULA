package com.oula.integration;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record IntegrationContract(
        UUID id,
        UUID workspaceId,
        UUID partnerId,
        String contractKey,
        String version,
        String direction,
        String purpose,
        String operation,
        String resourceType,
        Set<String> allowedDataClasses,
        String status,
        Instant effectiveFrom,
        Instant effectiveUntil,
        UUID createdBy,
        Instant createdAt
) {
    public IntegrationContract {
        allowedDataClasses = Set.copyOf(allowedDataClasses);
    }
}
