package com.oula.iam.dev;

import com.oula.transaction.TransactionApplicationService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development-only fictional deal. This is not a brokerage mandate or a real transaction.
 * Domain create() records initial stage history and the outbox atomically.
 */
@Component
@Profile("dev-auth & !prod")
final class DevBrokerDealFixture {
    static final UUID INTENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    static final UUID PROPERTY_ID = UUID.fromString("30000000-0000-4000-8000-000000000005");
    static final UUID TRANSACTION_ID = UUID.fromString("50000000-0000-4000-8000-000000000004");
    private static final UUID CORRELATION = UUID.fromString("60000000-0000-4000-8000-000000000004");

    private final DevAuthFixtureCatalog catalog;
    private final JdbcClient jdbc;
    private final TransactionApplicationService transactions;

    DevBrokerDealFixture(DevAuthFixtureCatalog catalog, JdbcClient jdbc,
                         TransactionApplicationService transactions) {
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @PostConstruct
    void seedFictionalReadOnlyDeal() {
        DevAuthFixture broker = catalog.require("broker-readonly");
        UUID workspace = broker.workspaceId();

        jdbc.sql("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max,
                     minimum_bedrooms, preferred_districts)
                values (:id, :workspace, 'BUY', 'ACTIVE', 1600000, 3, '["الملقا"]'::jsonb)
                on conflict (id) do nothing
                """).param("id", INTENT_ID).param("workspace", workspace).update();

        jdbc.sql("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (:id, :workspace, 'RESIDENTIAL', 'الملقا', 3, 1350000)
                on conflict (id) do nothing
                """).param("id", PROPERTY_ID).param("workspace", workspace).update();

        Long exists = jdbc.sql("""
                select count(*) from tx.transaction where id = :id and workspace_id = :workspace
                """).param("id", TRANSACTION_ID).param("workspace", workspace)
                .query(Long.class).single();

        if (exists == 0L) {
            transactions.create(TRANSACTION_ID, workspace, INTENT_ID, PROPERTY_ID,
                    broker.actorId(), CORRELATION);
        }
    }
}
