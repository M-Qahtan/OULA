package com.oula.api;

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

/**
 * Independent QA negative-contract gates for the real Intent API.
 * Requires the PostgreSQL/PostGIS-backed Spring Boot application context.
 * Uses fictional workspaces and declared intents only; never verified property facts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntentAuthorizationRegressionTest {
    private static final String PURPOSE = "PROPERTY_DECISION_SUPPORT";
    private static final String CREATE_BODY = """
            {
              "intentType": "BUY",
              "budgetMax": 1500000,
              "minimumBedrooms": 3,
              "preferredDistricts": ["الملقا"]
            }
            """;
    private static final String DIFFERENT_BODY = """
            {
              "intentType": "RENT",
              "budgetMax": 100000,
              "minimumBedrooms": 2,
              "preferredDistricts": ["حطين"]
            }
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void missingAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                        .header("X-OULA-Workspace-ID", UUID.randomUUID())
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongWorkspaceClaimIsForbidden() throws Exception {
        UUID requestedWorkspace = UUID.randomUUID();
        UUID tokenWorkspace = UUID.randomUUID();
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                        .with(token(tokenWorkspace, PURPOSE, "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", requestedWorkspace)
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isForbidden());
    }

    @Test
    void mismatchedPurposeHeaderIsForbidden() throws Exception {
        UUID workspace = UUID.randomUUID();
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                        .with(token(workspace, PURPOSE, "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingPurposeClaimIsForbidden() throws Exception {
        UUID workspace = UUID.randomUUID();
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                        .with(token(workspace, "TRANSACTION_EXECUTION", "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingReadScopeIsForbidden() throws Exception {
        UUID workspace = UUID.randomUUID();
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                        .with(token(workspace, PURPOSE, "oula.intent.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingWriteScopeCannotCreateIntent() throws Exception {
        UUID workspace = UUID.randomUUID();
        mvc.perform(post("/v1/intents")
                        .with(token(workspace, PURPOSE, "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE)
                        .header("Idempotency-Key", "qa-denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedForeignWorkspaceLookupDoesNotExposeAnotherTenantsIntent() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        UUID intent = UUID.randomUUID();
        seedWorkspace(owner);
        seedWorkspace(foreign);
        jdbc.update("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values (?, ?, 'BUY', 'ACTIVE', 1500000, 3, '["الملقا"]'::jsonb)
                """, intent, owner);

        // Token, request header and scope are valid for foreign, but the row belongs to owner.
        mvc.perform(get("/v1/intents/{id}", intent)
                        .with(token(foreign, PURPOSE, "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", foreign)
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isNotFound());

        // The owner's properly authorized read must still succeed.
        mvc.perform(get("/v1/intents/{id}", intent)
                        .with(token(owner, PURPOSE, "oula.intent.read"))
                        .header("X-OULA-Workspace-ID", owner)
                        .header("X-OULA-Purpose", PURPOSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(owner.toString()));
    }

    @Test
    void idempotencyReplaysIdenticalRequestButRejectsChangedPayload() throws Exception {
        UUID workspace = UUID.randomUUID();
        seedWorkspace(workspace);
        String key = "qa-intent-" + UUID.randomUUID();

        String firstResponse = mvc.perform(post("/v1/intents")
                        .with(token(workspace, PURPOSE, "oula.intent.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();

        String replayResponse = mvc.perform(post("/v1/intents")
                        .with(token(workspace, PURPOSE, "oula.intent.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replayResponse).isEqualTo(firstResponse);

        mvc.perform(post("/v1/intents")
                        .with(token(workspace, PURPOSE, "oula.intent.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(DIFFERENT_BODY))
                .andExpect(status().isConflict());

        Long count = jdbc.queryForObject(
                "select count(*) from intent.intent where workspace_id = ?", Long.class, workspace);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeCreate() throws Exception {
        UUID workspace = UUID.randomUUID();
        mvc.perform(post("/v1/intents")
                        .with(token(workspace, PURPOSE, "oula.intent.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", PURPOSE)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor token(UUID workspace, String purpose, String scope) {
        return jwt()
                .jwt(j -> j
                        .subject("qa-subject")
                        .claim("actor_id", UUID.randomUUID().toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    private void seedWorkspace(UUID workspace) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'QA Fixture', 'ACTIVE')",
                workspace
        );
    }
}
