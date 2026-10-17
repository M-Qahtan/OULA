package com.oula.api;

import com.oula.platform.DeterministicUuid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
class TransactionDealRoomApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createQualifyViewingOfferIsWorkspaceBoundIdempotentAndHumanConfirmed() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(propertyId, workspace);

        String createKey = "create-" + UUID.randomUUID();
        UUID transactionId = DeterministicUuid.from(
                "transaction.create.v1.entity",
                workspace,
                createKey
        );

        String createBody = """
                {
                  "intentId": "%s",
                  "propertyId": "%s"
                }
                """.formatted(intentId, propertyId);

        mvc.perform(post("/v1/transactions")
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", createKey)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.stage").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(post("/v1/transactions")
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", createKey)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(transactionId.toString()));

        assertThat(count("tx.transaction")).isEqualTo(1);
        assertThat(count("tx.stage_history")).isEqualTo(1);
        assertThat(count("platform.outbox_event")).isEqualTo(1);

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "qualify-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 0,
                                  "target": "QUALIFIED",
                                  "reason": "buyer confirmed readiness for a viewing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("QUALIFIED"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/v1/transactions/{transactionId}/deal-room", transactionId)
                        .with(token(
                                actor,
                                workspace,
                                "TRANSACTION_EXECUTION",
                                "oula.transaction.read",
                                "oula.transaction.viewing.request"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.stage").value("QUALIFIED"))
                .andExpect(jsonPath("$.availableCommands[0]").value("REQUEST_VIEWING"))
                .andExpect(jsonPath("$.humanConfirmationRequired").value(true));

        String viewingKey = "view-" + UUID.randomUUID();
        mvc.perform(post("/v1/transactions/{transactionId}/viewings", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.viewing.request"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", viewingKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "preferredAt": "2026-10-20T16:30:00Z",
                                  "note": "Preferred family viewing window",
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.transaction.stage").value("VIEWING"))
                .andExpect(jsonPath("$.transaction.version").value(2))
                .andExpect(jsonPath("$.viewing.status").value("REQUESTED"))
                .andExpect(jsonPath("$.viewing.requestedBy").value(actor.toString()));

        mvc.perform(post("/v1/transactions/{transactionId}/viewings", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.viewing.request"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", viewingKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "preferredAt": "2026-10-20T16:30:00Z",
                                  "note": "Preferred family viewing window",
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(count("tx.viewing_request")).isEqualTo(1);

        mvc.perform(get("/v1/transactions/{transactionId}/deal-room", transactionId)
                        .with(token(
                                actor,
                                workspace,
                                "TRANSACTION_EXECUTION",
                                "oula.transaction.read",
                                "oula.transaction.offer.submit"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.stage").value("VIEWING"))
                .andExpect(jsonPath("$.viewings.length()").value(1))
                .andExpect(jsonPath("$.availableCommands[0]").value("SUBMIT_OFFER"));

        String offerKey = "offer-" + UUID.randomUUID();
        mvc.perform(post("/v1/transactions/{transactionId}/offers", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.offer.submit"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", offerKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "amount": 1050000.00,
                                  "currency": "sar",
                                  "termsSummary": "Subject to document review; no external payment or signing implied.",
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.transaction.stage").value("OFFERING"))
                .andExpect(jsonPath("$.transaction.version").value(3))
                .andExpect(jsonPath("$.offer.amount").value(1050000.00))
                .andExpect(jsonPath("$.offer.currency").value("SAR"))
                .andExpect(jsonPath("$.offer.status").value("SUBMITTED"));

        mvc.perform(post("/v1/transactions/{transactionId}/offers", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.offer.submit"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", offerKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "amount": 1050000.00,
                                  "currency": "SAR",
                                  "termsSummary": "Subject to document review; no external payment or signing implied.",
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mvc.perform(get("/v1/transactions/{transactionId}/deal-room", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.stage").value("OFFERING"))
                .andExpect(jsonPath("$.viewings.length()").value(1))
                .andExpect(jsonPath("$.offers.length()").value(1));

        assertThat(count("tx.transaction_offer")).isEqualTo(1);
        assertThat(count("tx.stage_history")).isEqualTo(4);
        assertThat(count("platform.outbox_event")).isEqualTo(6);
    }

    @Test
    void unsafeOrUnauthorizedDealRoomCommandsFailClosed() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID otherWorkspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID otherPropertyId = UUID.randomUUID();
        seedWorkspace(workspace);
        seedWorkspace(otherWorkspace);
        seedIntent(intentId, workspace);
        seedProperty(propertyId, workspace);
        seedProperty(otherPropertyId, otherWorkspace);

        mvc.perform(post("/v1/transactions")
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "cross-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "intentId": "%s",
                                  "propertyId": "%s"
                                }
                                """.formatted(intentId, otherPropertyId)))
                .andExpect(status().isForbidden());

        String createKey = "safe-" + UUID.randomUUID();
        UUID transactionId = DeterministicUuid.from(
                "transaction.create.v1.entity",
                workspace,
                createKey
        );
        mvc.perform(post("/v1/transactions")
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", createKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "intentId": "%s",
                                  "propertyId": "%s"
                                }
                                """.formatted(intentId, propertyId)))
                .andExpect(status().isCreated());

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "qualify-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 0,
                                  "target": "QUALIFIED",
                                  "reason": "qualified"
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/transactions/{transactionId}/transitions", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "bypass-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "target": "VIEWING",
                                  "reason": "attempt to bypass dedicated command"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/v1/transactions/{transactionId}/viewings", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.viewing.request"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "no-human-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "humanConfirmed": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/v1/transactions/{transactionId}/viewings", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.advance"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "wrong-scope-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions/{transactionId}/viewings", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.viewing.request"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "stale-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 0,
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(stage(transactionId)).isEqualTo("QUALIFIED");
        assertThat(count("tx.viewing_request")).isZero();
        assertThat(count("tx.transaction_offer")).isZero();
    }

    @Test
    void offerRequiresPersistedViewingEvenIfTransactionWasSeededAtViewingStage() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(propertyId, workspace);
        jdbc.update("""
                insert into tx.transaction
                    (id, workspace_id, intent_id, property_id, stage, status, version)
                values
                    (?, ?, ?, ?, 'VIEWING', 'OPEN', 2)
                """, transactionId, workspace, intentId, propertyId);
        jdbc.update("""
                insert into tx.actor_deal_grant
                    (transaction_id,workspace_id,actor_id,role,active,granted_by)
                values (?, ?, ?, 'ORIGINATOR', true, ?)
                """, transactionId, workspace, actor, actor);

        mvc.perform(post("/v1/transactions/{transactionId}/offers", transactionId)
                        .with(token(actor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.offer.submit"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                        .header("Idempotency-Key", "offer-without-view-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedVersion": 2,
                                  "amount": 900000,
                                  "currency": "SAR",
                                  "humanConfirmed": true
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(stage(transactionId)).isEqualTo("VIEWING");
        assertThat(count("tx.transaction_offer")).isZero();
    }

    private RequestPostProcessor token(
            UUID actor,
            UUID workspace,
            String purpose,
            String... scopes
    ) {
        SimpleGrantedAuthority[] authorities = Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt()
                .jwt(jwt -> jwt
                        .subject("subject-" + actor)
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of(purpose)))
                .authorities(authorities);
    }

    @Test
    void sameWorkspaceKeyCannotReplayDifferentActorTransactionCreationReceipt() throws Exception {
        UUID workspace = UUID.randomUUID(), owner = UUID.randomUUID(), anotherActor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID(), propertyId = UUID.randomUUID();
        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(propertyId, workspace);
        String key = "actor-bound-deal-" + UUID.randomUUID();
        String payload = """
                {"intentId":"%s","propertyId":"%s"}
                """.formatted(intentId, propertyId);

        mvc.perform(post("/v1/transactions")
                .with(token(owner, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                .header("X-OULA-Workspace-ID", workspace)
                .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                .header("Idempotency-Key", key)
                .contentType("application/json").content(payload))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "false"));
        mvc.perform(post("/v1/transactions")
                .with(token(owner, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                .header("X-OULA-Workspace-ID", workspace)
                .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                .header("Idempotency-Key", key)
                .contentType("application/json").content(payload))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "true"));
        mvc.perform(post("/v1/transactions")
                .with(token(anotherActor, workspace, "TRANSACTION_EXECUTION", "oula.transaction.create"))
                .header("X-OULA-Workspace-ID", workspace)
                .header("X-OULA-Purpose", "TRANSACTION_EXECUTION")
                .header("Idempotency-Key", key)
                .contentType("application/json").content(payload))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:oula:problem:request-conflict"));
        assertThat(count("tx.transaction")).isEqualTo(1L);
        assertThat(count("tx.stage_history")).isEqualTo(1L);
    }

    @Test
    void dealReadRequiresExplicitActorGrantAndRevocationRemovesReadAccess() throws Exception {
        UUID workspace=UUID.randomUUID(), originator=UUID.randomUUID(),
             viewer=UUID.randomUUID(), ungranted=UUID.randomUUID();
        UUID intentId=UUID.randomUUID(), propertyId=UUID.randomUUID();
        seedWorkspace(workspace);
        seedIntent(intentId,workspace);
        seedProperty(propertyId,workspace);
        String createKey="granted-deal-"+UUID.randomUUID();
        String payload="""
                {"intentId":"%s","propertyId":"%s"}
                """.formatted(intentId,propertyId);
        UUID id=DeterministicUuid.from("transaction.create.v1.entity",workspace,createKey);
        mvc.perform(post("/v1/transactions")
                .with(token(originator,workspace,"TRANSACTION_EXECUTION","oula.transaction.create"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",createKey)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated());

        // Workspace membership, readable scope and even transition scope are NOT deal access.
        mvc.perform(get("/v1/transactions/{id}/deal-room",id)
                .with(token(viewer,workspace,"TRANSACTION_EXECUTION",
                        "oula.transaction.read","oula.transaction.advance"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/transactions/{id}",id)
                .with(token(ungranted,workspace,"TRANSACTION_EXECUTION","oula.transaction.read"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());

        String grantKey="share-"+UUID.randomUUID();
        String body="""
                {"actorId":"%s","expiresAt":"%s"}
                """.formatted(viewer,java.time.Instant.now().plusSeconds(3600*24));
        mvc.perform(post("/v1/transactions/{id}/read-grants",id)
                .with(token(viewer,workspace,"TRANSACTION_EXECUTION","oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","viewer-cannot-share")
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions/{id}/read-grants",id)
                .with(token(originator,workspace,"TRANSACTION_EXECUTION","oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",grantKey)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(header().string("Idempotency-Replayed","false"));

        mvc.perform(post("/v1/transactions/{id}/read-grants",id)
                .with(token(originator,workspace,"TRANSACTION_EXECUTION","oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",grantKey)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));

        mvc.perform(get("/v1/transactions/{id}/deal-room",id)
                .with(token(viewer,workspace,"TRANSACTION_EXECUTION",
                        "oula.transaction.read","oula.transaction.advance"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.stage").value("DRAFT"))
                .andExpect(jsonPath("$.availableCommands.length()").value(0));
        mvc.perform(post("/v1/transactions/{id}/transitions",id)
                .with(token(viewer,workspace,"TRANSACTION_EXECUTION","oula.transaction.advance"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","read-grant-is-not-command")
                .contentType("application/json")
                .content("""
                        {"expectedVersion":0,"target":"QUALIFIED","reason":"unauthorized viewer"}
                        """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions/{id}/read-grants/{actorId}/revoke",id,viewer)
                .with(token(originator,workspace,"TRANSACTION_EXECUTION","oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","revoke-"+UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/v1/transactions/{id}/deal-room",id)
                .with(token(viewer,workspace,"TRANSACTION_EXECUTION","oula.transaction.read"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/transactions/{id}/deal-room",id)
                .with(token(originator,workspace,"TRANSACTION_EXECUTION",
                        "oula.transaction.read","oula.transaction.advance"))
                .header("X-OULA-Workspace-ID",workspace)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCommands[0]").value("QUALIFY"));
    }

    private void seedWorkspace(UUID workspace) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Deal Room Test', 'ACTIVE')",
                workspace
        );
    }

    private void seedIntent(UUID intentId, UUID workspace) {
        jdbc.update("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values
                    (?, ?, 'BUY', 'ACTIVE', 1500000, 3, '["Al Malqa"]'::jsonb)
                """, intentId, workspace);
    }

    private void seedProperty(UUID propertyId, UUID workspace) {
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values
                    (?, ?, 'RESIDENTIAL', 'Al Malqa', 3, 1100000)
                """, propertyId, workspace);
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0L : count;
    }

    private String stage(UUID transactionId) {
        return jdbc.queryForObject(
                "select stage from tx.transaction where id = ?",
                String.class,
                transactionId
        );
    }
}
