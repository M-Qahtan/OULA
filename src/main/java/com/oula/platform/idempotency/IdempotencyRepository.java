package com.oula.platform.idempotency;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
class IdempotencyRepository {
    private final JdbcClient jdbc;

    IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean claim(UUID workspaceId, String key, String operation, String requestHash, OffsetDateTime expiresAt) {
        int inserted = jdbc.sql("""
                insert into platform.idempotency_record
                    (workspace_id, idempotency_key, operation, request_hash, state, expires_at)
                values
                    (:workspaceId, :key, :operation, :requestHash, 'IN_PROGRESS', :expiresAt)
                on conflict (workspace_id, idempotency_key) do nothing
                """)
                .param("workspaceId", workspaceId)
                .param("key", key)
                .param("operation", operation)
                .param("requestHash", requestHash)
                .param("expiresAt", expiresAt)
                .update();
        return inserted == 1;
    }

    Optional<StoredIdempotencyRecord> find(UUID workspaceId, String key) {
        return jdbc.sql("""
                select operation, request_hash, state, response_status, response_body::text
                  from platform.idempotency_record
                 where workspace_id = :workspaceId
                   and idempotency_key = :key
                """)
                .param("workspaceId", workspaceId)
                .param("key", key)
                .query((rs, rowNum) -> new StoredIdempotencyRecord(
                        rs.getString("operation"),
                        rs.getString("request_hash"),
                        rs.getString("state"),
                        (Integer) rs.getObject("response_status"),
                        rs.getString("response_body")
                ))
                .optional();
    }

    void complete(UUID workspaceId, String key, int responseStatus, String responseBody) {
        int updated = jdbc.sql("""
                update platform.idempotency_record
                   set state = 'COMPLETED',
                       response_status = :responseStatus,
                       response_body = cast(:responseBody as jsonb)
                 where workspace_id = :workspaceId
                   and idempotency_key = :key
                   and state = 'IN_PROGRESS'
                """)
                .param("workspaceId", workspaceId)
                .param("key", key)
                .param("responseStatus", responseStatus)
                .param("responseBody", responseBody)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("idempotency record could not be completed");
        }
    }

    void release(UUID workspaceId, String key) {
        jdbc.sql("""
                delete from platform.idempotency_record
                 where workspace_id = :workspaceId
                   and idempotency_key = :key
                   and state = 'IN_PROGRESS'
                """)
                .param("workspaceId", workspaceId)
                .param("key", key)
                .update();
    }

    OffsetDateTime expiresInHours(long hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours);
    }
}
