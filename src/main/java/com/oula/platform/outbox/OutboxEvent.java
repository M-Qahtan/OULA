package com.oula.platform.outbox;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record OutboxEvent(UUID eventId, String eventType, UUID aggregateId, UUID workspaceId, UUID correlationId, Instant occurredAt, String payload) {
  public OutboxEvent {
    Objects.requireNonNull(eventId); Objects.requireNonNull(eventType); Objects.requireNonNull(aggregateId); Objects.requireNonNull(workspaceId); Objects.requireNonNull(correlationId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(payload);
  }
}
