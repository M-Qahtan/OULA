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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyManagementApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void managementApiIsPurposeAndScopeBound() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seed(workspace, property);

        mvc.perform(post("/v1/properties/{propertyId}/management/enroll", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.management.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "enroll-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String dueAt = Instant.now().plusSeconds(3600).toString();
        mvc.perform(post("/v1/properties/{propertyId}/obligations", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.management.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "obligation-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "obligationType":"INSURANCE_RENEWAL",
                                  "title":"Renew property insurance",
                                  "dueAt":"%s",
                                  "priority":"CRITICAL",
                                  "sourceType":"POLICY",
                                  "sourceReference":"policy-123"
                                }
                                """.formatted(dueAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(post("/v1/properties/{propertyId}/guardian/assess", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.guardian.run"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "assess-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalsCreated").value(1))
                .andExpect(jsonPath("$.actionsCreated").value(1));

        mvc.perform(get("/v1/properties/{propertyId}/management", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.management.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obligations.length()").value(1))
                .andExpect(jsonPath("$.guardianSignals.length()").value(1))
                .andExpect(jsonPath("$.actions.length()").value(1));

        mvc.perform(get("/v1/properties/{propertyId}/management", property)
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT",
                                "oula.property.management.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(UUID actor, UUID workspace, String purpose, String scope) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("subject-" + actor)
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Management API", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
    }
}
