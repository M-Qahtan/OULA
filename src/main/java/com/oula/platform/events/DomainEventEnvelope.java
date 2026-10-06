package com.oula.platform.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        UUID aggregateId,
        UUID workspaceId,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        Map<String, Object> payload
) {}
