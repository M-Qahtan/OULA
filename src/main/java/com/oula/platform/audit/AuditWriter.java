package com.oula.platform.audit;

import com.oula.platform.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditWriter {
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public AuditWriter(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public UUID append(
            UUID workspaceId,
            UUID actorId,
            String actorSubject,
            String purpose,
            String action,
            String aggregateType,
            UUID aggregateId,
            UUID traceId,
            Map<String, ?> details
    ) {
        UUID auditId = UuidV7.next();
        jdbc.sql("""
                insert into platform.audit_log
                    (id, workspace_id, actor_id, actor_subject, purpose, action,
                     aggregate_type, aggregate_id, trace_id, details)
                values
                    (:id, :workspaceId, :actorId, :actorSubject, :purpose, :action,
                     :aggregateType, :aggregateId, :traceId, cast(:details as jsonb))
                """)
                .param("id", auditId)
                .param("workspaceId", workspaceId)
                .param("actorId", actorId)
                .param("actorSubject", actorSubject)
                .param("purpose", purpose)
                .param("action", action)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("traceId", traceId)
                .param("details", serialize(details))
                .update();
        return auditId;
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize audit details", ex);
        }
    }
}
