package com.oula.operations;

import java.time.Instant;
import java.util.UUID;

public record Obligation(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        String obligationType,
        String title,
        Instant dueAt,
        String priority,
        String status,
        String sourceType,
        String sourceReference,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt,
        long version
) {}
