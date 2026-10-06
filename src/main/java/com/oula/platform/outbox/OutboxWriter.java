package com.oula.platform.outbox;

import com.oula.platform.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class OutboxWriter {
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public OutboxWriter(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public UUID append(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            UUID workspaceId,
            UUID correlationId,
            UUID causationId,
            Object payload
    ) {
        UUID eventId = UuidV7.next();
        String serialized = serialize(payload);

        jdbc.sql("""
                insert into platform.outbox_event
                    (event_id, event_type, aggregate_type, aggregate_id, workspace_id,
                     correlation_id, causation_id, occurred_at, payload)
                values
                    (:eventId, :eventType, :aggregateType, :aggregateId, :workspaceId,
                     :correlationId, :causationId, :occurredAt, cast(:payload as jsonb))
                """)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("workspaceId", workspaceId)
                .param("correlationId", correlationId)
                .param("causationId", causationId)
                .param("occurredAt", OffsetDateTime.now(ZoneOffset.UTC))
                .param("payload", serialized)
                .update();

        return eventId;
    }

    private String serialize(Object payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize outbox payload", ex);
        }
    }
}
