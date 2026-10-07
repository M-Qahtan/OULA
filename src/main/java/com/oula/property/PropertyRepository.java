package com.oula.property;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class PropertyRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    PropertyRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insertAsset(
            UUID propertyId,
            UUID workspaceId,
            String assetType,
            String district,
            Integer bedrooms
    ) {
        jdbc.sql("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, version)
                values
                    (:id, :workspaceId, :assetType, :district, :bedrooms, 0)
                """)
                .param("id", propertyId)
                .param("workspaceId", workspaceId)
                .param("assetType", assetType)
                .param("district", district)
                .param("bedrooms", bedrooms)
                .update();
    }

    PropertyAssetView findOwned(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, asset_type, district, bedrooms, version
                  from property.asset
                 where id = :propertyId
                   and workspace_id = :workspaceId
                """)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new PropertyAssetView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("asset_type"),
                        rs.getString("district"),
                        (Integer) rs.getObject("bedrooms"),
                        rs.getLong("version")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property not found"));
    }

    void insertFact(
            UUID factId,
            UUID workspaceId,
            UUID propertyId,
            UUID actorId,
            RecordPropertyFactCommand command
    ) {
        findOwned(workspaceId, propertyId);
        jdbc.sql("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status,
                     source_type, confidence, visibility, evidence_reference,
                     recorded_by, version)
                values
                    (:id, :propertyId, :factKey, cast(:value as jsonb), :truthStatus,
                     :sourceType, :confidence, :visibility, :evidenceReference,
                     :recordedBy, 0)
                """)
                .param("id", factId)
                .param("propertyId", propertyId)
                .param("factKey", command.factKey())
                .param("value", write(command.value()))
                .param("truthStatus", command.truthStatus().name())
                .param("sourceType", command.sourceType())
                .param("confidence", command.confidence())
                .param("visibility", command.visibility())
                .param("evidenceReference", command.evidenceReference())
                .param("recordedBy", actorId)
                .update();
    }

    PropertyFactView findFact(UUID workspaceId, UUID propertyId, UUID factId) {
        return jdbc.sql("""
                select f.id, f.property_id, f.fact_key, f.value_json::text,
                       f.truth_status, f.source_type, f.confidence, f.visibility,
                       f.evidence_reference, f.verified_by, f.verified_at, f.version
                  from property.fact f
                  join property.asset p on p.id = f.property_id
                 where f.id = :factId
                   and f.property_id = :propertyId
                   and p.workspace_id = :workspaceId
                """)
                .param("factId", factId)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new PropertyFactView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("property_id", UUID.class),
                        rs.getString("fact_key"),
                        readMap(rs.getString("value_json")),
                        TruthStatus.valueOf(rs.getString("truth_status")),
                        rs.getString("source_type"),
                        rs.getBigDecimal("confidence") == null
                                ? null
                                : rs.getBigDecimal("confidence").doubleValue(),
                        rs.getString("visibility"),
                        rs.getObject("evidence_reference", UUID.class),
                        rs.getObject("verified_by", UUID.class),
                        rs.getObject("verified_at", OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("verified_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("version")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property fact not found"));
    }

    void verifyFact(
            UUID workspaceId,
            UUID propertyId,
            UUID factId,
            long expectedVersion,
            UUID evidenceReference,
            UUID verifiedBy,
            OffsetDateTime verifiedAt
    ) {
        int updated = jdbc.sql("""
                update property.fact f
                   set truth_status = 'VERIFIED',
                       evidence_reference = :evidenceReference,
                       verified_by = :verifiedBy,
                       verified_at = :verifiedAt,
                       updated_at = now(),
                       version = version + 1
                  from property.asset p
                 where f.id = :factId
                   and f.property_id = :propertyId
                   and p.id = f.property_id
                   and p.workspace_id = :workspaceId
                   and f.truth_status in ('DECLARED','OBSERVED','CALCULATED','ESTIMATED')
                   and f.version = :expectedVersion
                """)
                .param("factId", factId)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .param("expectedVersion", expectedVersion)
                .param("evidenceReference", evidenceReference)
                .param("verifiedBy", verifiedBy)
                .param("verifiedAt", verifiedAt)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("fact verification state/version conflict");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize property fact", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize property fact", ex);
        }
    }
}
