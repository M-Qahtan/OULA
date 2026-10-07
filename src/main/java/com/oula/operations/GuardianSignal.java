package com.oula.operations;

import java.time.Instant;
import java.util.UUID;

public record GuardianSignal(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID obligationId,
        String signalType,
        String severity,
        String status,
        String message,
        String recommendedAction,
        Instant detectedAt,
        Instant resolvedAt,
        long version
) {}
