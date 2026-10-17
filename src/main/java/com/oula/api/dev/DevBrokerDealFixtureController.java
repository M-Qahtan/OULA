package com.oula.api.dev;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.transaction.TransactionApplicationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/** DEV-only request-scoped fixture controller in the permitted API coordination module. */
@RestController
@RequestMapping("/dev-auth/broker")
@Profile("dev-auth & !prod")
class DevBrokerDealFixtureController {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID WORKSPACE = UUID.fromString("20000000-0000-4000-8000-000000000004");
    private final WorkspacePurposeAuthorizer authorizer;
    private final DevBrokerDealFixtureService fixtures;

    DevBrokerDealFixtureController(WorkspacePurposeAuthorizer authorizer,
                                   DevBrokerDealFixtureService fixtures) {
        this.authorizer = authorizer;
        this.fixtures = fixtures;
    }

    @PostMapping("/prepare")
    ResponseEntity<PreparedBrokerFixture> prepare(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        AccessContext access = authorizer.require(authentication, workspaceId, purpose,
                AccessPurpose.TRANSACTION_EXECUTION, "oula.transaction.read");
        if (!WORKSPACE.equals(access.workspaceId()) || !ACTOR.equals(access.actorId())) {
            throw new AccessDeniedException("isolated fictional broker identity required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(fixtures.prepare(access.workspaceId(), access.actorId()));
    }

    record PreparedBrokerFixture(String mode, UUID workspaceId, UUID transactionId,
                                 boolean verifiedExternalData) {}
}

/** Lazy transactional preparation; no startup mutation or cross-test outbox pollution. */
@Service
@Profile("dev-auth & !prod")
class DevBrokerDealFixtureService {
    private static final UUID INTENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID PROPERTY_ID = UUID.fromString("30000000-0000-4000-8000-000000000005");
    private static final UUID TRANSACTION_ID = UUID.fromString("50000000-0000-4000-8000-000000000004");
    private static final UUID CORRELATION = UUID.fromString("60000000-0000-4000-8000-000000000004");
    private static final UUID FICTIONAL_OWNER = UUID.fromString("10000000-0000-4000-8000-000000000099");

    private final JdbcClient jdbc;
    private final TransactionApplicationService transactions;

    DevBrokerDealFixtureService(JdbcClient jdbc, TransactionApplicationService transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Transactional
    public DevBrokerDealFixtureController.PreparedBrokerFixture prepare(UUID workspace, UUID actor) {
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
                    FICTIONAL_OWNER, CORRELATION);
        }
        // Reconnecting does not create duplicate access-grant outbox events.
        Long validGrant=jdbc.sql("""
                select count(*) from tx.actor_deal_grant
                 where transaction_id=:tx and workspace_id=:ws and actor_id=:actor
                   and role='VIEWER' and active=true and expires_at > now()
                """).param("tx",TRANSACTION_ID).param("ws",workspace)
                .param("actor",actor).query(Long.class).single();
        if (validGrant == 0L) {
            transactions.grantRead(TRANSACTION_ID,workspace,FICTIONAL_OWNER,actor,
                    java.time.Instant.now().plusSeconds(86400L*14),CORRELATION);
        }
        return new DevBrokerDealFixtureController.PreparedBrokerFixture(
                "DEV_TEST_ONLY", workspace, TRANSACTION_ID, false);
    }
}
