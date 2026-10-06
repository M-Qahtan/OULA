package com.oula.platform.outbox;

import java.time.Instant;
import java.util.UUID;

public record StoredOutboxEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID workspaceId,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        String payload
) {
}
