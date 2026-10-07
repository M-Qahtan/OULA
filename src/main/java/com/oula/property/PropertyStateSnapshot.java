package com.oula.property;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PropertyStateSnapshot(
        UUID snapshotId,
        UUID workspaceId,
        UUID propertyId,
        int version,
        Instant effectiveAt,
        Instant recordedAt,
        String stateBasis,
        Map<String, Object> state,
        String sourceType,
        String sourceReference,
        UUID supersedesSnapshotId
) {
}
