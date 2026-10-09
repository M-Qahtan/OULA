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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RentalLifecycleAdvisoryApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void unknownOccupancyRequiresHumanReviewAndDedicatedReadScope() throws Exception {
        UUID workspace=UUID.randomUUID(), property=UUID.randomUUID(),
             unit=UUID.randomUUID(), actor=UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC);
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                workspace,"PERSONAL","Rental Risk", "ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,property,workspace,"RESIDENTIAL","Al Yasmin",3,1_000_000);
        jdbc.update("""
                insert into tenancy.unit
                 (id,workspace_id,property_id,unit_code,status,created_by,created_at)
                values (?,?,?,?,'ACTIVE',?,?)
                """,unit,workspace,property,"A-001",actor,now);

        int before=jdbc.queryForObject(
                "select count(*) from platform.outbox_event where workspace_id=?",
                Integer.class, workspace);

        mvc.perform(get("/v1/properties/{id}/rental-lifecycle-advice",property)
                        .with(token(actor,workspace,"PROPERTY_MANAGEMENT",
                                "oula.property.rental-advisory.read"))
                        .header("X-OULA-Workspace-ID",workspace)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.assessmentState")
                        .value("INSUFFICIENT_OCCUPANCY_EVIDENCE"))
                .andExpect(jsonPath("$.unknownOccupancyUnits").value(1))
                .andExpect(jsonPath("$.recommendations[0].actionCode")
                        .value("VERIFY_UNIT_HANDOVER_RECORD"))
                .andExpect(jsonPath("$.recommendations[0].executionGate")
                        .value("HUMAN_REVIEW_REQUIRED"));

        mvc.perform(get("/v1/properties/{id}/rental-lifecycle-advice",property)
                        .with(token(actor,workspace,"PROPERTY_MANAGEMENT",
                                "oula.property.advisory.read"))
                        .header("X-OULA-Workspace-ID",workspace)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/properties/{id}/rental-lifecycle-advice",property)
                        .with(token(actor,workspace,"PROPERTY_DECISION_SUPPORT",
                                "oula.property.rental-advisory.read"))
                        .header("X-OULA-Workspace-ID",workspace)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/properties/{id}/rental-lifecycle-advice",property)
                        .with(token(actor,workspace,"PROPERTY_MANAGEMENT",
                                "oula.property.rental-advisory.read"))
                        .header("X-OULA-Workspace-ID",UUID.randomUUID())
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        int after=jdbc.queryForObject(
                "select count(*) from platform.outbox_event where workspace_id=?",
                Integer.class, workspace);
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    private RequestPostProcessor token(UUID actor,UUID workspace,String purpose,String scope) {
        return jwt().jwt(jwt -> jwt.subject("subject-"+actor)
                        .claim("actor_id",actor.toString())
                        .claim("oula_workspace_ids",List.of(workspace.toString()))
                        .claim("oula_purposes",List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }
}
