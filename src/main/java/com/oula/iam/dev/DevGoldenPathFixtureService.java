package com.oula.iam.dev;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Profile("dev-auth & !prod")
class DevGoldenPathFixtureService {
    static final String MODE = "DEV_TEST_ONLY";
    static final String SIGNAL_SOURCE = "DEV_FIXTURE_SIMULATION";

    private final JdbcClient jdbc;
    private final DevAuthFixtureCatalog fixtures;

    private final List<PropertyFixture> properties = List.of(
            new PropertyFixture(
                    UUID.fromString("30000000-0000-4000-8000-000000000001"),
                    UUID.fromString("31000000-0000-4000-8000-000000000001"),
                    "APARTMENT",
                    "الملقا",
                    3,
                    1_090_000,
                    172,
                    18
            ),
            new PropertyFixture(
                    UUID.fromString("30000000-0000-4000-8000-000000000002"),
                    UUID.fromString("31000000-0000-4000-8000-000000000002"),
                    "APARTMENT",
                    "الملقا",
                    4,
                    1_180_000,
                    188,
                    24
            ),
            new PropertyFixture(
                    UUID.fromString("30000000-0000-4000-8000-000000000003"),
                    UUID.fromString("31000000-0000-4000-8000-000000000003"),
                    "APARTMENT",
                    "الملقا",
                    3,
                    960_000,
                    160,
                    31
            )
    );

    DevGoldenPathFixtureService(
            JdbcClient jdbc,
            DevAuthFixtureCatalog fixtures
    ) {
        this.jdbc = jdbc;
        this.fixtures = fixtures;
    }

    @PostConstruct
    void seedFictionalProperties() {
        DevAuthFixture buyer = fixtures.require("buyer");
        for (PropertyFixture property : properties) {
            jdbc.sql("""
                    insert into property.asset
                      (id, workspace_id, asset_type, district, bedrooms, asking_price)
                    values
                      (:id, :workspaceId, :assetType, :district, :bedrooms, :askingPrice)
                    on conflict (id) do update set
                      workspace_id = excluded.workspace_id,
                      asset_type = excluded.asset_type,
                      district = excluded.district,
                      bedrooms = excluded.bedrooms,
                      asking_price = excluded.asking_price,
                      updated_at = now()
                    """)
                    .param("id", property.id())
                    .param("workspaceId", buyer.workspaceId())
                    .param("assetType", property.assetType())
                    .param("district", property.district())
                    .param("bedrooms", property.bedrooms())
                    .param("askingPrice", property.askingPrice())
                    .update();

            String valueJson = """
                    {"value":%d,"unit":"sqm","fixture":true}
                    """.formatted(property.areaSqm()).trim();
            jdbc.sql("""
                    insert into property.fact
                      (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                    values
                      (:id, :propertyId, 'areaSqm', cast(:valueJson as jsonb),
                       'DECLARED', 'DEV_FIXTURE', 0.5)
                    on conflict (id) do update set
                      value_json = excluded.value_json,
                      truth_status = 'DECLARED',
                      source_type = 'DEV_FIXTURE',
                      confidence = 0.5
                    """)
                    .param("id", property.areaFactId())
                    .param("propertyId", property.id())
                    .param("valueJson", valueJson)
                    .update();
        }
    }

    @Transactional
    PreparedFixture prepareIntent(UUID intentId, UUID workspaceId) {
        boolean exists = jdbc.sql("""
                select count(*)
                  from intent.intent
                 where id = :intentId
                   and workspace_id = :workspaceId
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single() == 1L;
        if (!exists) {
            throw new NoSuchElementException("intent not found in requested dev workspace");
        }

        for (PropertyFixture property : properties) {
            jdbc.sql("""
                    insert into matching.intent_property_signal
                      (intent_id, property_id, commute_minutes, source_type, confidence)
                    values
                      (:intentId, :propertyId, :commuteMinutes, :sourceType, 0.5)
                    on conflict (intent_id, property_id) do update set
                      commute_minutes = excluded.commute_minutes,
                      source_type = excluded.source_type,
                      confidence = excluded.confidence,
                      observed_at = now()
                    """)
                    .param("intentId", intentId)
                    .param("propertyId", property.id())
                    .param("commuteMinutes", property.commuteMinutes())
                    .param("sourceType", SIGNAL_SOURCE)
                    .update();
        }

        return new PreparedFixture(
                MODE,
                intentId,
                properties.stream().map(PropertyFixture::id).toList(),
                SIGNAL_SOURCE,
                false
        );
    }

    record PreparedFixture(
            String mode,
            UUID intentId,
            List<UUID> propertyIds,
            String signalSource,
            boolean verifiedExternalData
    ) {
        PreparedFixture {
            propertyIds = List.copyOf(propertyIds);
        }
    }

    private record PropertyFixture(
            UUID id,
            UUID areaFactId,
            String assetType,
            String district,
            int bedrooms,
            int askingPrice,
            int areaSqm,
            int commuteMinutes
    ) {
    }
}
