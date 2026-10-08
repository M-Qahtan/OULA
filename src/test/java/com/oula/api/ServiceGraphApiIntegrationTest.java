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
class ServiceGraphApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void providerRegistrationIsPropertyManagementPurposeAndScopeBound() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID providerParty = UUID.randomUUID();
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Service Graph API", "ACTIVE"
        );

        mvc.perform(post("/v1/service-providers")
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.services.provider.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "provider-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "providerPartyId":"%s",
                                  "displayName":"Riyadh Property Services"
                                }
                                """.formatted(providerParty)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerPartyId").value(providerParty.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));

        mvc.perform(post("/v1/service-providers")
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT",
                                "oula.services.provider.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "provider-denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "providerPartyId":"%s",
                                  "displayName":"Denied Provider"
                                }
                                """.formatted(UUID.randomUUID())))
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
}
