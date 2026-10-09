package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.*;
import com.oula.vitals.PropertyVitalsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InterventionApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PropertyManagementService management;
    @Autowired PropertyVitalsService vitals;
    @Autowired JsonMapper json;

    @Test
    void reviewAndOutcomeHttpFlowIsIdempotentAndWorkspaceScoped() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seed(workspace, property);
        AccessContext access = new AccessContext(
                actor, "review-api", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        management.enroll(access, property, UUID.randomUUID());
        management.createObligation(access, property,
                new CreateObligationCommand(
                        "SAFETY_CHECK", "Overdue fire inspection",
                        Instant.now().minusSeconds(3600), "HIGH",
                        "INSPECTION_PLAN", "safety-18"), UUID.randomUUID());
        management.assess(access, property, UUID.randomUUID());
        var before = vitals.assess(access, property, UUID.randomUUID());

        String request = """
                {"sourceSnapshotId":"%s","dimension":"OBLIGATIONS",
                 "actionCode":"REVIEW_OBLIGATIONS","decision":"ACKNOWLEDGED",
                 "rationale":"Reviewed manually; no automatic execution"}
                """.formatted(before.id());

        var response = mvc.perform(post("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.review.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "review-one")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.baselineStatus").value("RED"))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andReturn();
        UUID reviewId = UUID.fromString(
                json.readTree(response.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(post("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.review.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "review-one")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mvc.perform(get("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1));

        mvc.perform(get("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.review.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "AUTONOMOUS_EXECUTION",
                                "oula.property.interventions.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        ActionItem action = management.overview(access, property).actions().getFirst();
        management.completeAction(access, action.id(), "Addressed", UUID.randomUUID());
        var after = vitals.assess(access, property, UUID.randomUUID());

        String observation = """
                {"afterSnapshotId":"%s","observationNote":
                 "A later assessment improved; correlation is not causation"}
                """.formatted(after.id());
        mvc.perform(post("/v1/intervention-reviews/{reviewId}/outcome", reviewId)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.outcome.write"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "outcome-one")
                        .contentType("application/json")
                        .content(observation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observedDirection").value("IMPROVED"))
                .andExpect(jsonPath("$.executionEvidenceLevel").value("OBSERVATION_ONLY"));

        mvc.perform(get("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.outcomes.length()").value(1));

        mvc.perform(get("/v1/properties/{propertyId}/intervention-reviews", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.interventions.read"))
                        .header("X-OULA-Workspace-ID", UUID.randomUUID())
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(
            UUID actor, UUID workspace, String purpose, String scope
    ) {
        return jwt()
                .jwt(jwt -> jwt.subject("subject-" + actor)
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    private void seed(UUID workspace, UUID property) {
        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Review API", "ACTIVE");
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """, property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000);
    }
}
