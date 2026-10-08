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
class PropertyOperationsApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PropertyManagementService management;

    @Test
    void workOrderApiRequiresPropertyManagementPurposeAndExplicitScopes() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        seed(workspace, property);

        AccessContext access = new AccessContext(
                actor, "api-seed", workspace, AccessPurpose.PROPERTY_MANAGEMENT
        );
        management.enroll(access, property, UUID.randomUUID());
        management.createObligation(
                access, property,
                new CreateObligationCommand(
                        "ELECTRICAL_INSPECTION", "Electrical inspection",
                        Instant.now().plusSeconds(3600), "HIGH", "INSPECTION_PLAN", "inspection-1"
                ),
                UUID.randomUUID()
        );
        management.assess(access, property, UUID.randomUUID());
        UUID action = management.overview(access, property).actions().getFirst().id();

        String created = mvc.perform(post("/v1/property-actions/{actionId}/work-orders", action)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-create-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "category":"ELECTRICAL",
                                  "title":"Inspect distribution board",
                                  "scopeDescription":"Inspect board, protection devices and documented defects",
                                  "estimatedCost":1200.00,
                                  "currency":"SAR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();

        String workOrderId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/v1/work-orders/{id}/approve", workOrderId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.approve"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-approve-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"approvedBudget\":1500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/v1/work-orders/{id}/assign", workOrderId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.execute"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-assign-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"providerPartyId\":\"" + provider + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mvc.perform(post("/v1/work-orders/{id}/start", workOrderId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.execute"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-start-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(post("/v1/work-orders/{id}/completion", workOrderId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.execute"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-complete-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "actualCost":1350.00,
                                  "completionEvidenceId":"%s",
                                  "completionNote":"Inspection complete and evidence attached"
                                }
                                """.formatted(evidence)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETION_REVIEW"));

        mvc.perform(post("/v1/work-orders/{id}/verify", workOrderId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.verify"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "wo-verify-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/v1/properties/{propertyId}/work-orders", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.workorder.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        mvc.perform(get("/v1/properties/{propertyId}/work-orders", property)
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT",
                                "oula.property.workorder.read"))
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
                workspaceId, "PERSONAL", "Operations API", "ACTIVE"
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
