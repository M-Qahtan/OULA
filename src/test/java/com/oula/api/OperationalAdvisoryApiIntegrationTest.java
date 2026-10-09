package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperationalAdvisoryApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void advisesFromImmutableVitalsWithoutExecutingAndRequiresSeparateReadScope() throws Exception {
        UUID workspace = UUID.randomUUID(), property = UUID.randomUUID(), actor = UUID.randomUUID();
        seed(workspace, property, actor);
        mvc.perform(post("/v1/properties/{propertyId}/vitals/assess", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.vitals.assess"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "advisory-assess-" + UUID.randomUUID()))
                .andExpect(status().isCreated());

        mvc.perform(get("/v1/properties/{propertyId}/operational-advice", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.advisory.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.propertyId").value(property.toString()))
                .andExpect(jsonPath("$.assessmentState").value("LIMITED_EVIDENCE"))
                .andExpect(jsonPath("$.observedTrend").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.recommendations.length()").value(6))
                .andExpect(jsonPath("$.recommendations[0].executionGate")
                        .value("HUMAN_REVIEW_REQUIRED"));

        mvc.perform(get("/v1/properties/{propertyId}/operational-advice", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.vitals.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/properties/{propertyId}/operational-advice", property)
                        .with(token(actor, workspace, "PROPERTY_DECISION_SUPPORT",
                                "oula.property.advisory.read"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        UUID otherWorkspace = UUID.randomUUID();
        mvc.perform(get("/v1/properties/{propertyId}/operational-advice", property)
                        .with(token(actor, workspace, "PROPERTY_MANAGEMENT",
                                "oula.property.advisory.read"))
                        .header("X-OULA-Workspace-ID", otherWorkspace)
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

    private void seed(UUID workspace, UUID property, UUID actor) {
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                workspace, "PERSONAL", "Advisory API", "ACTIVE");
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """, property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000);
        jdbc.update("""
                insert into ops.management_enrollment
                    (id, workspace_id, property_id, manager_actor_id, status, activated_at, version)
                values (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), workspace, property, actor,
                "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC), 0);
    }
}
