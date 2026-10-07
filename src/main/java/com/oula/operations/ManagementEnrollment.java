package com.oula.operations;

import java.time.Instant;
import java.util.UUID;

public record ManagementEnrollment(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID managerActorId,
        String status,
        Instant activatedAt,
        Instant closedAt,
        long version
) {}
