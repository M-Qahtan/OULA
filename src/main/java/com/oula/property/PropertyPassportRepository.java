package com.oula.property;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class PropertyPassportRepository {
    private final JdbcClient jdbc;

    PropertyPassportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    AssetRow asset(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select id, workspace_id, asset_type, district, bedrooms, asking_price
                  from property.asset
                 where id = :propertyId
                   and workspace_id = :workspaceId
                """)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new AssetRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("asset_type"),
                        rs.getString("district"),
                        (Integer) rs.getObject("bedrooms"),
                        rs.getBigDecimal("asking_price")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property not found"));
    }

    List<PropertyPassportFact> facts(UUID propertyId) {
        return jdbc.sql("""
                select id, fact_key, value_json::text as value_json, truth_status,
                       source_type, confidence, valid_from, valid_to
                  from property.fact
                 where property_id = :propertyId
                 order by fact_key, created_at
                """)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> new PropertyPassportFact(
                        rs.getObject("id", UUID.class),
                        rs.getString("fact_key"),
                        rs.getString("value_json"),
                        rs.getString("truth_status"),
                        rs.getString("source_type"),
                        rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                        instant(rs.getObject("valid_from", OffsetDateTime.class)),
                        instant(rs.getObject("valid_to", OffsetDateTime.class))
                ))
                .list();
    }

    private java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    record AssetRow(
            UUID propertyId,
            UUID workspaceId,
            String assetType,
            String district,
            Integer bedrooms,
            java.math.BigDecimal askingPrice
    ) {}
}
