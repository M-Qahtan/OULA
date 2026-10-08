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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntegrationTrustApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void integrationAdministrationRequiresDedicatedPurposeAndScope()
            throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seed(workspace);

        mvc.perform(post("/v1/integrations/partners")
                        .with(token(
                                actor, workspace,
                                "INTEGRATION_OPERATIONS",
                                "oula.integration.partner.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "INTEGRATION_OPERATIONS")
                        .header("Idempotency-Key", "partner-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "partnerCode":"REGISTRY_A",
                                  "partnerType":"REGISTRY",
                                  "displayName":"Registry A",
                                  "jurisdiction":"SA",
                                  "authMode":"JWS",
                                  "credentialReference":"secrets://registry-a/key",
                                  "inboundEnabled":true,
                                  "outboundEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnerCode").value("REGISTRY_A"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));

        mvc.perform(post("/v1/integrations/partners")
                        .with(token(
                                actor, workspace,
                                "PROPERTY_MANAGEMENT",
                                "oula.integration.partner.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "INTEGRATION_OPERATIONS")
                        .header("Idempotency-Key", "denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "partnerCode":"REGISTRY_DENIED",
                                  "partnerType":"REGISTRY",
                                  "displayName":"Denied",
                                  "jurisdiction":"SA",
                                  "authMode":"JWS",
                                  "credentialReference":"secrets://denied/key",
                                  "inboundEnabled":true,
                                  "outboundEnabled":false
                                }
                                """))
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

    private void seed(UUID workspaceId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Integration API", "ACTIVE"
        );
    }
}
