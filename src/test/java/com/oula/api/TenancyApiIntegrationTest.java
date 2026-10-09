package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.tenancy.TenancyService;
import com.oula.tenancy.RentalUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenancyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void unitRegistrationIsIdempotentAndPurposeScopeBound() throws Exception {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),actor=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Tenancy API","ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",3,1_200_000);

        String key="unit-register-"+UUID.randomUUID();
        var first=mvc.perform(post("/v1/properties/{id}/units",p)
                    .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.write"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                    .header("Idempotency-Key",key)
                    .contentType("application/json").content("{\"unitCode\":\"A-101\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitCode").value("A-101"))
                .andExpect(header().string("Idempotency-Replayed","false"))
                .andReturn();

        mvc.perform(post("/v1/properties/{id}/units",p)
                    .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.write"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                    .header("Idempotency-Key",key)
                    .contentType("application/json").content("{\"unitCode\":\"A-101\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));

        mvc.perform(post("/v1/properties/{id}/units",p)
                    .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.write"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                    .header("Idempotency-Key",key)
                    .contentType("application/json").content("{\"unitCode\":\"B-202\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/v1/properties/{id}/occupancy",p)
                    .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.read"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("UNKNOWN"));

        mvc.perform(get("/v1/properties/{id}/occupancy",p)
                    .with(token(actor,w,"PROPERTY_DECISION_SUPPORT","oula.tenancy.read"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/properties/{id}/occupancy",p)
                    .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.write"))
                    .header("X-OULA-Workspace-ID",w)
                    .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(UUID actor,UUID w,String purpose,String scope){
        return jwt().jwt(jwt->jwt.subject("subject-"+actor)
                        .claim("actor_id",actor.toString())
                        .claim("oula_workspace_ids",List.of(w.toString()))
                        .claim("oula_purposes",List.of(purpose)))
                .authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
    }
}
