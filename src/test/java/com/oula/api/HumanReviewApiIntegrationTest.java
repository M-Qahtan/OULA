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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HumanReviewApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reviewCaptureUsesIdempotencyAndSeparateHumanAuthorityScopes() throws Exception {
        UUID w=UUID.randomUUID(), p=UUID.randomUUID(), u=UUID.randomUUID(), actor=UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Review API","ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",4,1_500_000);
        jdbc.update("""
                insert into tenancy.unit(id,workspace_id,property_id,unit_code,status,created_by,created_at)
                values (?,?,?,?,'ACTIVE',?,?)
                """,u,w,p,"A",actor,now);
        String key="capture-"+UUID.randomUUID();
        String body="""
                {"recommendationIndex":0,
                 "expectedRulesVersion":"rental-lifecycle-advisory-v1",
                 "expectedActionCode":"VERIFY_UNIT_HANDOVER_RECORD",
                 "expectedUnitId":"%s"}
                """.formatted(u);

        String result=mvc.perform(post("/v1/properties/{propertyId}/advisory-reviews",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.capture"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key",key)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","false"))
                .andExpect(jsonPath("$.dimension").value("OCCUPANCY_EVIDENCE"))
                .andReturn().getResponse().getContentAsString();
        UUID caseId=UUID.fromString(result.replaceAll(".*\"id\":\"([^\"]+)\".*","$1"));

        mvc.perform(post("/v1/properties/{propertyId}/advisory-reviews",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.capture"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key",key)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));

        mvc.perform(get("/v1/advisory-reviews/{id}",caseId)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.read"))
                        .header("X-OULA-Workspace-ID",w).header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.history.length()").value(0));

        mvc.perform(post("/v1/advisory-reviews/{id}/decisions",caseId)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.decide"))
                        .header("X-OULA-Workspace-ID",w).header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key","decide-"+UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"decision\":\"ACCEPT_FOR_REVIEW\",\"rationale\":\"Manager will inspect\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("DECISION"));

        mvc.perform(get("/v1/advisory-reviews/{id}",caseId)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.capture"))
                        .header("X-OULA-Workspace-ID",w).header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/advisory-reviews/{id}",caseId)
                        .with(token(actor,w,"PROPERTY_DECISION_SUPPORT","oula.advisory.review.read"))
                        .header("X-OULA-Workspace-ID",w).header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/advisory-reviews/{id}",caseId)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.advisory.review.read"))
                        .header("X-OULA-Workspace-ID",UUID.randomUUID())
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(UUID actor,UUID w,String purpose,String scope) {
        return jwt().jwt(j->j.subject("subject-"+actor)
                        .claim("actor_id",actor.toString())
                        .claim("oula_workspace_ids",List.of(w.toString()))
                        .claim("oula_purposes",List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }
}
