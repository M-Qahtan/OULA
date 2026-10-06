package com.oula.api;

import com.oula.platform.outbox.OutboxDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoldenPathApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxDispatchService outboxDispatcher;

    @Test
    void securedGoldenPathIsPurposeBoundIdempotentAndOutboxBacked() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(propertyId, workspace);
        seedSignal(intentId, propertyId);
        seedVerifiedFact(propertyId);
        seedTransaction(transactionId, workspace, intentId, propertyId);

        String matchKey = "match-" + UUID.randomUUID();

        mvc.perform(post("/v1/intents/{intentId}/matches", intentId)
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT", "oula.matching.run"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", matchKey))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.matchRunId").exists())
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].propertyId").value(propertyId.toString()));

        mvc.perform(post("/v1/intents/{intentId}/matches", intentId)
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT", "oula.matching.run"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", matchKey))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(count("matching.match_run")).isEqualTo(1L);
        assertThat(count("matching.match_result")).isEqualTo(1L);

        String transitionBody = """
                {
                  "expectedVersion": 0,
                  "target": "QUALIFIED",
                  "reason": "qualified for the next transaction stage"
                }
                """;
        String transitionKey = "tx-" + UUID.randomUUID();

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", transitionKey)
                        .contentType("application/json")
                        .content(transitionBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.stage").value("QUALIFIED"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", transitionKey)
                        .contentType("application/json")
                        .content(transitionBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.stage").value("QUALIFIED"));

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", transitionKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 0,
                                  "target": "QUALIFIED",
                                  "reason": "different payload under the same key"
                                }
                                """))
                .andExpect(status().isConflict());

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "stale-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(transitionBody))
                .andExpect(status().isConflict());

        mvc.perform(get("/v1/transactions/{transactionId}", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("QUALIFIED"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(count("tx.stage_history")).isEqualTo(1L);
        assertThat(count("platform.outbox_event")).isEqualTo(2L);

        assertThat(outboxDispatcher.dispatchBatch(10)).isEqualTo(2);
        Long unpublished = jdbc.queryForObject(
                "select count(*) from platform.outbox_event where published_at is null",
                Long.class
        );
        assertThat(unpublished).isZero();
    }

    @Test
    void unauthenticatedRequestsFailClosed() throws Exception {
        mvc.perform(get("/v1/transactions/{transactionId}", UUID.randomUUID())
                        .header("X-OULA-Workspace-ID", UUID.randomUUID())
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workspaceAndPurposeAreNotTrustedFromHeadersAlone() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestedWorkspace = UUID.randomUUID();
        UUID tokenWorkspace = UUID.randomUUID();

        mvc.perform(get("/v1/transactions/{transactionId}", UUID.randomUUID())
                        .with(token(actor, tokenWorkspace, "TRANSACTION_EXECUTION", "oula.transaction.read"))
                        .header("X-OULA-Workspace-ID", requestedWorkspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/transactions/{transactionId}", UUID.randomUUID())
                        .with(token(actor, requestedWorkspace, "PROPERTY_DECISION_SUPPORT", "oula.transaction.read"))
                        .header("X-OULA-Workspace-ID", requestedWorkspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(
            UUID actor,
            UUID workspace,
            String purpose,
            String scope
    ) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("subject-" + actor)
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    private void seedWorkspace(UUID workspace) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'API Test', 'ACTIVE')",
                workspace
        );
    }

    private void seedIntent(UUID intentId, UUID workspace) {
        jdbc.update("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values
                    (?, ?, 'BUY', 'ACTIVE', 2000000, 4, '["Al Yasmin"]'::jsonb)
                """, intentId, workspace);
    }

    private void seedProperty(UUID propertyId, UUID workspace) {
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values
                    (?, ?, 'RESIDENTIAL', 'Al Yasmin', 4, 1800000)
                """, propertyId, workspace);
    }

    private void seedSignal(UUID intentId, UUID propertyId) {
        jdbc.update("""
                insert into matching.intent_property_signal
                    (intent_id, property_id, commute_minutes, source_type, confidence)
                values
                    (?, ?, 22, 'ROUTING_ENGINE', 0.95)
                """, intentId, propertyId);
    }

    private void seedVerifiedFact(UUID propertyId) {
        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values
                    (?, ?, 'bedrooms_verified', '{"value":4}'::jsonb, 'VERIFIED', 'INSPECTION', 1.0)
                """, UUID.randomUUID(), propertyId);
    }

    private void seedTransaction(
            UUID transactionId,
            UUID workspace,
            UUID intentId,
            UUID propertyId
    ) {
        jdbc.update("""
                insert into tx.transaction
                    (id, workspace_id, intent_id, property_id, stage, status, version)
                values
                    (?, ?, ?, ?, 'DRAFT', 'OPEN', 0)
                """, transactionId, workspace, intentId, propertyId);
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }
}
