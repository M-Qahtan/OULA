package com.oula.platform.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
class OutboxDispatchRepository {
    private final JdbcClient jdbc;

    OutboxDispatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<StoredOutboxEvent> lockNextBatch(int limit) {
        return jdbc.sql("""
                select event_id, event_type, aggregate_type, aggregate_id, workspace_id,
                       correlation_id, causation_id, occurred_at, payload::text
                  from platform.outbox_event
                 where published_at is null
                 order by occurred_at, event_id
                 for update skip locked
                 limit :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new StoredOutboxEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("correlation_id", UUID.class),
                        rs.getObject("causation_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getString("payload")
                ))
                .list();
    }

    void markPublished(UUID eventId) {
        jdbc.sql("""
                update platform.outbox_event
                   set published_at = :publishedAt,
                       publish_attempts = publish_attempts + 1,
                       last_error = null
                 where event_id = :eventId
                """)
                .param("eventId", eventId)
                .param("publishedAt", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
    }

    void markFailed(UUID eventId, String error) {
        jdbc.sql("""
                update platform.outbox_event
                   set publish_attempts = publish_attempts + 1,
                       last_error = :error
                 where event_id = :eventId
                """)
                .param("eventId", eventId)
                .param("error", error == null ? "unknown publication failure" : error.substring(0, Math.min(error.length(), 2000)))
                .update();
    }
}
