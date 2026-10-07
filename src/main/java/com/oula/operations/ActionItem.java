package com.oula.operations;

import java.time.Instant;
import java.util.UUID;

public record ActionItem(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID obligationId,
        UUID guardianSignalId,
        String actionType,
        String title,
        String status,
        Instant dueAt,
        UUID assignedActorId,
        String resolutionNote,
        Instant createdAt,
        Instant completedAt,
        long version
) {}
