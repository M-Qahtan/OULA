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
class ComplianceApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void autonomousEvaluationIsPurposeAndScopeBoundAndRequiresApproval() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        seed(workspace);

        mvc.perform(post("/v1/compliance/evaluate")
                        .with(token(actor, workspace, "AUTONOMOUS_EXECUTION",
                                "oula.compliance.evaluate"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "AUTONOMOUS_EXECUTION")
                        .header("Idempotency-Key", "policy-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "action":"SERVICE_QUOTE_SELECT",
                                  "resourceType":"WorkOrder",
                                  "resourceId":"%s",
                                  "jurisdiction":"SA",
                                  "amount":4200.00,
                                  "currency":"SAR",
                                  "verificationPresent":true,
                                  "evidencePresent":true,
                                  "context":{"channel":"agent"}
                                }
                                """.formatted(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("AUTONOMOUS_EXECUTION"))
                .andExpect(jsonPath("$.decision").value("REQUIRE_APPROVAL"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("POLICY_APPROVAL_REQUIRED"));

        mvc.perform(post("/v1/compliance/evaluate")
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.compliance.evaluate"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "AUTONOMOUS_EXECUTION")
                        .header("Idempotency-Key", "policy-denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "action":"SERVICE_QUOTE_SELECT",
                                  "resourceType":"WorkOrder",
                                  "resourceId":"%s",
                                  "jurisdiction":"SA",
                                  "verificationPresent":true,
                                  "evidencePresent":true
                                }
                                """.formatted(resource)))
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
                workspaceId, "PERSONAL", "Compliance API", "ACTIVE"
        );
    }
}
