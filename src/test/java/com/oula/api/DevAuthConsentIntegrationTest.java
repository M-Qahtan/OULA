package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev-auth")
@Transactional
class DevAuthConsentIntegrationTest {

    private static final UUID BUYER_WORKSPACE =
            UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID MANAGER_WORKSPACE =
            UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Test
    void signedLocalJwtDrivesRealResourceServerConsentGrantRevokeAndReplay() throws Exception {
        String token = issue("buyer");

        mvc.perform(get("/v1/consents")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        String grant = """
                {
                  "consentCode": "LIFE_CONTEXT_PROCESSING",
                  "decision": "GRANTED",
                  "reason": "User chose to enable life-context decision support"
                }
                """;

        mvc.perform(post("/v1/consents/decisions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "consent-grant-001")
                        .contentType("application/json")
                        .content(grant))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.consentCode").value("LIFE_CONTEXT_PROCESSING"))
                .andExpect(jsonPath("$.decision").value("GRANTED"))
                .andExpect(jsonPath("$.source").value("USER_EXPLICIT"));

        mvc.perform(post("/v1/consents/decisions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "consent-grant-001")
                        .contentType("application/json")
                        .content(grant))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(count("select count(*) from iam.consent_event")).isEqualTo(1);
        assertThat(count("""
                select count(*) from platform.audit_log
                 where action = 'iam.consent.granted'
                """)).isEqualTo(1);
        assertThat(count("""
                select count(*) from platform.outbox_event
                 where event_type = 'iam.consent.changed.v1'
                """)).isEqualTo(1);

        mvc.perform(post("/v1/consents/decisions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "consent-revoke-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "consentCode": "LIFE_CONTEXT_PROCESSING",
                                  "decision": "REVOKED",
                                  "reason": "User withdrew life-context processing consent"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REVOKED"));

        mvc.perform(get("/v1/consents")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consentCode").value("LIFE_CONTEXT_PROCESSING"))
                .andExpect(jsonPath("$[0].decision").value("REVOKED"));

        assertThat(count("select count(*) from iam.consent_event")).isEqualTo(2);
    }

    @Test
    void resourceServerFailsClosedForMissingOrCrossWorkspaceAuthority() throws Exception {
        mvc.perform(get("/v1/consents")
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:oula:problem:unauthorized"));

        String token = issue("buyer");
        mvc.perform(get("/v1/consents")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", MANAGER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:oula:problem:forbidden"));

        mvc.perform(get("/v1/consents")
                        .header("Authorization", "Bearer " + token)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        String readOnly = issue("buyer-readonly");
        mvc.perform(post("/v1/consents/decisions")
                        .header("Authorization", "Bearer " + readOnly)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "readonly-denied")
                        .contentType("application/json")
                        .content("""
                                {
                                  "consentCode": "LOCATION_CONTEXT",
                                  "decision": "GRANTED"
                                }
                                """))
                .andExpect(status().isForbidden());

        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        mvc.perform(get("/v1/consents")
                        .header("Authorization", "Bearer " + tampered)
                        .header("X-OULA-Workspace-ID", BUYER_WORKSPACE)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyConfiguredLocalBrowserOrigin() throws Exception {
        mvc.perform(options("/v1/consents")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers",
                                "authorization,x-oula-workspace-id,x-oula-purpose"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mvc.perform(options("/v1/consents")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void fixtureUsesFixedWorkspaceAndScopes() throws Exception {
        String body = mvc.perform(post("/dev-auth/token")
                        .contentType("application/json")
                        .content("{\"fixture\":\"buyer\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fixture").value("buyer"))
                .andExpect(jsonPath("$.workspaceId").value(BUYER_WORKSPACE.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> decoded = json.readValue(
                body,
                new TypeReference<Map<String, Object>>() {}
        );
        assertThat(String.valueOf(decoded.get("scopes"))).contains("oula.transaction.create");
        assertThat(String.valueOf(decoded.get("scopes"))).doesNotContain("admin");
    }

    private String issue(String fixture) throws Exception {
        String body = mvc.perform(post("/dev-auth/token")
                        .contentType("application/json")
                        .content("{\"fixture\":\"" + fixture + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEV_TEST_ONLY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> decoded = json.readValue(
                body,
                new TypeReference<Map<String, Object>>() {}
        );
        return String.valueOf(decoded.get("accessToken"));
    }

    private long count(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
