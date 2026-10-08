package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutonomousOperationsApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PropertyManagementService management;
    @Autowired OperationsExecutionService execution;

    @Test
    void autonomousApprovalEndpointReturnsPolicyPreconditionAndVerifyRemainsHumanOnly()
            throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID humanActor = UUID.randomUUID();
        UUID agentActor = UUID.randomUUID();
        seed(workspace, property);

        AccessContext human = new AccessContext(
                humanActor, "api-human", workspace,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        management.enroll(human, property, UUID.randomUUID());
        management.createObligation(
                human, property,
                new CreateObligationCommand(
                        "ELECTRICAL_INSPECTION",
                        "Electrical inspection",
                        Instant.now().plusSeconds(3600),
                        "HIGH",
                        "INSPECTION_PLAN",
                        "wave10-api"
                ),
                UUID.randomUUID()
        );
        management.assess(human, property, UUID.randomUUID());
        UUID actionId = management.overview(human, property)
                .actions().getFirst().id();
        WorkOrder workOrder = execution.createFromAction(
                human,
                actionId,
                new CreateWorkOrderCommand(
                        "ELECTRICAL",
                        "Inspect board",
                        "Inspect distribution board and protection devices",
                        new BigDecimal("1200.00"),
                        "SAR"
                ),
                UUID.randomUUID()
        );

        mvc.perform(post("/v1/work-orders/{id}/approve", workOrder.id())
                        .with(token(
                                agentActor, workspace,
                                "AUTONOMOUS_EXECUTION",
                                "oula.autonomous.workorder.approve"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "AUTONOMOUS_EXECUTION")
                        .header("X-OULA-Jurisdiction", "SA")
                        .header("Idempotency-Key", "auto-approve-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"approvedBudget\":1500.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.decision").value("REQUIRE_APPROVAL"))
                .andExpect(jsonPath("$.policyDecisionId").exists())
                .andExpect(jsonPath("$.reasonCodes[0]").value("POLICY_APPROVAL_REQUIRED"));

        mvc.perform(post("/v1/work-orders/{id}/verify", workOrder.id())
                        .with(token(
                                agentActor, workspace,
                                "AUTONOMOUS_EXECUTION",
                                "oula.property.workorder.verify"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "AUTONOMOUS_EXECUTION")
                        .header("Idempotency-Key", "auto-verify-" + UUID.randomUUID()))
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

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Wave 10 API", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL",
                "Al Yasmin", 4, 1_800_000
        );
    }
}
