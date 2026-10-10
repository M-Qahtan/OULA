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

/** Negative JWT/workspace/purpose/scope and idempotency regressions for #47. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyIntakeApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String PURPOSE = "PROPERTY_DECISION_SUPPORT";
    private static final String PROPERTY_BODY = """
            {"assetType":"RESIDENTIAL","district":"الملقا",
             "bedrooms":3,"askingPrice":1250000,"demo":true}
            """;

    @Test
    void propertyIntakeFailsClosedWithoutJwtOrMatchingWorkspacePurposeAndScope() throws Exception {
        UUID workspace = workspace();
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/v1/properties").headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/properties").with(token(actor, UUID.randomUUID(),
                PURPOSE, "oula.property.write")).headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/v1/properties").with(token(actor, workspace,
                "PROPERTY_MANAGEMENT", "oula.property.write"))
                .headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/v1/properties").with(token(actor, workspace,
                PURPOSE, "oula.property.read")).headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isForbidden());

        Long count = jdbc.queryForObject(
                "select count(*) from property.asset where workspace_id = ?", Long.class, workspace);
        assertThat(count).isZero();
    }

    @Test
    void idempotencyReplayIsStableAndChangedPayloadConflicts() throws Exception {
        UUID workspace = workspace();
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        var first = mvc.perform(post("/v1/properties")
                .with(token(actor, workspace, PURPOSE, "oula.property.write"))
                .headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "false"))
            .andExpect(jsonPath("$.dataOrigin").value("DEMO"))
            .andExpect(jsonPath("$.truthStatus").value("DECLARED"))
            .andReturn();

        mvc.perform(post("/v1/properties")
                .with(token(actor, workspace, PURPOSE, "oula.property.write"))
                .headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "true"))
            .andExpect(jsonPath("$.id").value(
                    com.jayway.jsonpath.JsonPath.read(
                            first.getResponse().getContentAsString(), "$.id")));

        mvc.perform(post("/v1/properties")
                .with(token(actor, workspace, PURPOSE, "oula.property.write"))
                .headers(headers(workspace, key))
                .contentType("application/json")
                .content(PROPERTY_BODY.replace("1250000", "1350000")))
            .andExpect(status().isConflict());

        Long count = jdbc.queryForObject(
                "select count(*) from property.asset where workspace_id = ?", Long.class, workspace);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void listingReadRequiresItsOwnScopeAndCannotCrossWorkspaces() throws Exception {
        UUID workspace = workspace();
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        var created = mvc.perform(post("/v1/properties")
                .with(token(actor, workspace, PURPOSE, "oula.property.write"))
                .headers(headers(workspace, key))
                .contentType("application/json").content(PROPERTY_BODY))
            .andExpect(status().isCreated()).andReturn();
        String propertyId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/v1/properties/{propertyId}", propertyId)
                .with(token(actor, workspace, PURPOSE, "oula.listing.read"))
                .header("X-OULA-Workspace-ID", workspace)
                .header("X-OULA-Purpose", PURPOSE))
            .andExpect(status().isForbidden());

        mvc.perform(post("/v1/properties/{propertyId}/listings", propertyId)
                .with(token(actor, UUID.randomUUID(), PURPOSE, "oula.listing.write"))
                .headers(headers(workspace, UUID.randomUUID().toString()))
                .contentType("application/json")
                .content("{\"transactionType\":\"SALE\",\"askingPrice\":1250000}"))
            .andExpect(status().isForbidden());
    }

    private UUID workspace() {
        UUID workspace = UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Intake Auth Test','ACTIVE')", workspace);
        return workspace;
    }

    private org.springframework.http.HttpHeaders headers(UUID workspace, String key) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-OULA-Workspace-ID", workspace.toString());
        headers.set("X-OULA-Purpose", PURPOSE);
        headers.set("Idempotency-Key", key);
        return headers;
    }

    private RequestPostProcessor token(UUID actor, UUID workspace, String purpose, String scope) {
        return jwt().jwt(j -> j.subject("subject-" + actor)
                .claim("actor_id", actor.toString())
                .claim("oula_workspace_ids", List.of(workspace.toString()))
                .claim("oula_purposes", List.of(purpose)))
            .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
