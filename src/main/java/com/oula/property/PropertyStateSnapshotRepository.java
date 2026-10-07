package com.oula.property;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class PropertyStateSnapshotRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    PropertyStateSnapshotRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void lockProperty(UUID workspaceId, UUID propertyId) {
        jdbc.sql("""
                select id
                  from property.asset
                 where id = :propertyId
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property not found"));
    }

    PropertyStateSnapshot latestRecorded(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, version, effective_at, recorded_at,
                       state_basis, state_json::text as state_json, source_type,
                       source_reference, supersedes_snapshot_id
                  from property.state_snapshot
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by version desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElse(null);
    }

    PropertyStateSnapshot latestKnownAt(UUID workspaceId, UUID propertyId, OffsetDateTime asOf) {
        return jdbc.sql("""
                select id, workspace_id, property_id, version, effective_at, recorded_at,
                       state_basis, state_json::text as state_json, source_type,
                       source_reference, supersedes_snapshot_id
                  from property.state_snapshot
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                   and effective_at <= :asOf
                   and recorded_at <= :asOf
                 order by effective_at desc, version desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("asOf", asOf)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property snapshot not found"));
    }

    void insert(
            PropertyStateSnapshot snapshot,
            List<UUID> evidenceRefs,
            UUID correlationId
    ) {
        jdbc.sql("""
                insert into property.state_snapshot
                    (id, workspace_id, property_id, version, effective_at, recorded_at,
                     state_basis, state_json, source_type, source_reference, evidence_refs,
                     supersedes_snapshot_id, correlation_id)
                values
                    (:id, :workspaceId, :propertyId, :version, :effectiveAt, :recordedAt,
                     :stateBasis, cast(:stateJson as jsonb), :sourceType, :sourceReference,
                     cast(:evidenceRefs as jsonb), :supersedesSnapshotId, :correlationId)
                """)
                .param("id", snapshot.snapshotId())
                .param("workspaceId", snapshot.workspaceId())
                .param("propertyId", snapshot.propertyId())
                .param("version", snapshot.version())
                .param("effectiveAt", OffsetDateTime.ofInstant(snapshot.effectiveAt(), ZoneOffset.UTC))
                .param("recordedAt", OffsetDateTime.ofInstant(snapshot.recordedAt(), ZoneOffset.UTC))
                .param("stateBasis", snapshot.stateBasis())
                .param("stateJson", write(snapshot.state()))
                .param("sourceType", snapshot.sourceType())
                .param("sourceReference", snapshot.sourceReference())
                .param("evidenceRefs", write(evidenceRefs))
                .param("supersedesSnapshotId", snapshot.supersedesSnapshotId())
                .param("correlationId", correlationId)
                .update();
    }

    private PropertyStateSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PropertyStateSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getInt("version"),
                rs.getObject("effective_at", OffsetDateTime.class).toInstant(),
                rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                rs.getString("state_basis"),
                readMap(rs.getString("state_json")),
                rs.getString("source_type"),
                rs.getString("source_reference"),
                rs.getObject("supersedes_snapshot_id", UUID.class)
        );
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize property snapshot", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize property snapshot", ex);
        }
    }
}
