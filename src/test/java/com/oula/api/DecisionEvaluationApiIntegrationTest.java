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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DecisionEvaluationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void evaluationIsPurposeScopedReadOnlyAndHandlesMissingOperationalSource() throws Exception {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),actor=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Evaluation API","ACTIVE");
        jdbc.update("""
                insert into property.asset
                    (id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",3,1_100_000);

        int auditBefore=jdbc.queryForObject(
                "select count(*) from platform.audit_log where workspace_id=?",
                Integer.class,w);
        int outboxBefore=jdbc.queryForObject(
                "select count(*) from platform.outbox_event where workspace_id=?",
                Integer.class,w);

        mvc.perform(get("/v1/properties/{id}/decision-evaluation",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT",
                                "oula.property.decision-evaluation.read"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.evidenceAssessment")
                        .value("NO_DOCUMENTED_OUTCOME_OBSERVATIONS"))
                .andExpect(jsonPath("$.operationalSourceStatus")
                        .value("NO_RECORDED_INTERVENTIONS"))
                .andExpect(jsonPath("$.operational.recordedReviews").value(0))
                .andExpect(jsonPath("$.automaticTrainingAllowed").value(false));

        mvc.perform(get("/v1/properties/{id}/decision-evaluation",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT",
                                "oula.advisory.review.read"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/properties/{id}/decision-evaluation",p)
                        .with(token(actor,w,"PROPERTY_DECISION_SUPPORT",
                                "oula.property.decision-evaluation.read"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/properties/{id}/decision-evaluation",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT",
                                "oula.property.decision-evaluation.read"))
                        .header("X-OULA-Workspace-ID",UUID.randomUUID())
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from platform.audit_log where workspace_id=?",
                Integer.class,w)).isEqualTo(auditBefore);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from platform.outbox_event where workspace_id=?",
                Integer.class,w)).isEqualTo(outboxBefore);
    }

    private RequestPostProcessor token(UUID actor,UUID workspace,String purpose,String scope) {
        return jwt().jwt(j->j.subject("subject-"+actor)
                        .claim("actor_id",actor.toString())
                        .claim("oula_workspace_ids",List.of(workspace.toString()))
                        .claim("oula_purposes",List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }
}
