package com.oula.leasing;

import java.time.Instant;
import java.util.UUID;

public record OccupancyPeriod(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID leaseId,
        UUID occupantPartyId,
        String status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant closedAt,
        long version
) {}
