package com.oula.intent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Repository
class IntentRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    IntentRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insert(
            UUID id,
            UUID workspaceId,
            UUID actorId,
            UpsertIntentCommand command
    ) {
        jdbc.sql("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max,
                     minimum_bedrooms, preferred_districts, created_by, version)
                values
                    (:id, :workspaceId, :intentType, 'DRAFT', :budgetMax,
                     :minimumBedrooms, cast(:districts as jsonb), :createdBy, 0)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("intentType", command.intentType())
                .param("budgetMax", command.budgetMax())
                .param("minimumBedrooms", command.minimumBedrooms())
                .param("districts", write(command.preferredDistricts()))
                .param("createdBy", actorId)
                .update();
    }

    IntentView find(UUID workspaceId, UUID intentId) {
        return jdbc.sql("""
                select id, workspace_id, intent_type, status, budget_max,
                       minimum_bedrooms, preferred_districts::text,
                       version, activated_at
                  from intent.intent
                 where id = :intentId
                   and workspace_id = :workspaceId
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new IntentView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("intent_type"),
                        IntentStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("budget_max"),
                        (Integer) rs.getObject("minimum_bedrooms"),
                        readSet(rs.getString("preferred_districts")),
                        rs.getLong("version"),
                        rs.getObject("activated_at", OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("activated_at", OffsetDateTime.class).toInstant()
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("intent not found"));
    }

    void updateDraft(
            UUID workspaceId,
            UUID intentId,
            long expectedVersion,
            UpsertIntentCommand command
    ) {
        int updated = jdbc.sql("""
                update intent.intent
                   set intent_type = :intentType,
                       budget_max = :budgetMax,
                       minimum_bedrooms = :minimumBedrooms,
                       preferred_districts = cast(:districts as jsonb),
                       updated_at = now(),
                       version = version + 1
                 where id = :intentId
                   and workspace_id = :workspaceId
                   and status = 'DRAFT'
                   and version = :expectedVersion
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .param("expectedVersion", expectedVersion)
                .param("intentType", command.intentType())
                .param("budgetMax", command.budgetMax())
                .param("minimumBedrooms", command.minimumBedrooms())
                .param("districts", write(command.preferredDistricts()))
                .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "intent draft version/state conflict"
            );
        }
    }

    void activate(UUID workspaceId, UUID intentId, long expectedVersion) {
        int updated = jdbc.sql("""
                update intent.intent
                   set status = 'ACTIVE',
                       activated_at = now(),
                       updated_at = now(),
                       version = version + 1
                 where id = :intentId
                   and workspace_id = :workspaceId
                   and status = 'DRAFT'
                   and version = :expectedVersion
                   and budget_max is not null
                   and budget_max > 0
                   and minimum_bedrooms is not null
                   and minimum_bedrooms >= 0
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "intent cannot be activated from current state/version"
            );
        }
    }

    private String write(Set<String> districts) {
        try {
            return json.writeValueAsString(
                    districts == null ? Set.of() : districts
            );
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize districts", ex);
        }
    }

    private Set<String> readSet(String value) {
        try {
            String[] values = json.readValue(value, String[].class);
            return Set.copyOf(Arrays.asList(values));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize districts", ex);
        }
    }
}
