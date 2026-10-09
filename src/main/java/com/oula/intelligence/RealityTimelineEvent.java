package com.oula.intelligence;

import java.time.Instant;
import java.util.UUID;

/**
 * A read-only pointer to a canonical record, not a new assertion of truth.
 */
public record RealityTimelineEvent(
        String kind,
        UUID sourceId,
        Instant occurredAt,
        String epistemicClass,
        String status
) {
}
