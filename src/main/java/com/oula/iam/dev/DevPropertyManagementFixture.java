package com.oula.iam.dev;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Isolated fictional owner asset for integrated development tests; never active in prod. */
@Component
@Profile("dev-auth & !prod")
final class DevPropertyManagementFixture {
    static final UUID PROPERTY_ID = UUID.fromString("30000000-0000-4000-8000-000000000004");
    private final DevAuthFixtureCatalog catalog;
    private final JdbcClient jdbc;

    DevPropertyManagementFixture(DevAuthFixtureCatalog catalog, JdbcClient jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void seed() {
        DevAuthFixture manager = catalog.require("property-manager");
        jdbc.sql("""
                insert into property.asset
                  (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (:id, :workspaceId, 'RESIDENTIAL', 'الياسمين', 4, 1800000)
                on conflict (id) do update set
                  workspace_id=excluded.workspace_id,
                  asset_type=excluded.asset_type,
                  district=excluded.district,
                  bedrooms=excluded.bedrooms,
                  asking_price=excluded.asking_price,
                  updated_at=now()
                """)
                .param("id", PROPERTY_ID)
                .param("workspaceId", manager.workspaceId()).update();
        jdbc.sql("""
                insert into property.fact
                  (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values
                  (:id, :propertyId, 'areaSqm',
                   cast('{"value":210,"unit":"sqm","fixture":true}' as jsonb),
                   'DECLARED', 'DEV_FIXTURE', 0.5)
                on conflict (id) do update set
                  truth_status='DECLARED', source_type='DEV_FIXTURE', confidence=0.5
                """)
                .param("id", UUID.fromString("31000000-0000-4000-8000-000000000004"))
                .param("propertyId", PROPERTY_ID).update();
    }
}
