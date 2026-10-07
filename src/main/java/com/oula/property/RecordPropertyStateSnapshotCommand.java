package com.oula.property;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecordPropertyStateSnapshotCommand(
        Instant effectiveAt,
        String stateBasis,
        Map<String, Object> state,
        String sourceType,
        String sourceReference,
        List<UUID> evidenceRefs
) {
}
