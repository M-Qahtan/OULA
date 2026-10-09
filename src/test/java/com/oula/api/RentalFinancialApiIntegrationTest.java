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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
class RentalFinancialApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void receiptRecordingIsIdempotentAndFinanceScopesAreDistinct() throws Exception {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),unit=UUID.randomUUID(),
             lease=UUID.randomUUID(),installment=UUID.randomUUID(),actor=UUID.randomUUID(),
             evidence=UUID.randomUUID();
        LocalDate start=LocalDate.now(ZoneId.of("Asia/Riyadh")).minusMonths(1);
        OffsetDateTime now=OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC);
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Rental API","ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",3,1_000_000);
        jdbc.update("""
                insert into tenancy.unit(id,workspace_id,property_id,unit_code,status,created_by,created_at)
                values (?,?,?,?,'ACTIVE',?,?)
                """,unit,w,p,"A",actor,now);
        jdbc.update("""
                insert into tenancy.lease
                  (id,workspace_id,unit_id,landlord_party_id,tenant_party_id,
                   start_on,end_on,periodic_rent,currency,rent_every_months,status,
                   signing_evidence_id,signed_at,activated_at,created_by,created_at)
                values (?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)
                """,lease,w,unit,UUID.randomUUID(),UUID.randomUUID(),start,start.plusMonths(12),
                new BigDecimal("1000"),"SAR",1,UUID.randomUUID(),now,now,actor,now);
        jdbc.update("""
                insert into tenancy.rent_installment
                   (id,workspace_id,lease_id,due_on,amount,currency,status,created_at)
                values (?,?,?,?,?,'SAR','SCHEDULED',?)
                """,installment,w,lease,start,new BigDecimal("1000"),now);
        jdbc.update("""
                insert into docs.evidence
                   (id,workspace_id,evidence_type,source,verification_status,content_hash,captured_at)
                values (?,?,'RENT_RECEIPT','DOCUMENT','VERIFIED',?,?)
                """,evidence,w,UUID.randomUUID().toString(),now);
        String key="rental-"+UUID.randomUUID();
        String body="""
                {"amount":250.00,"evidenceId":"%s","note":"Rent receipt document review"}
                """.formatted(evidence);
        mvc.perform(post("/v1/leases/{lease}/installments/{id}/receipt-evidence",lease,installment)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.finance.record"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key",key).contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","false"))
                .andExpect(jsonPath("$.entryType").value("RECEIPT"));

        mvc.perform(post("/v1/leases/{lease}/installments/{id}/receipt-evidence",lease,installment)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.finance.record"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key",key).contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));

        mvc.perform(get("/v1/leases/{lease}/financial-evidence",lease)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.finance.read"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netDocumentaryReceipts").value(250.0))
                .andExpect(jsonPath("$.uncoveredDueThroughDate").value(750.0));

        mvc.perform(get("/v1/properties/{propertyId}/occupancy-insight",p)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.occupancy.read"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unknownUnits").value(1))
                .andExpect(jsonPath("$.coverageStatus").value("NO_EVIDENCE"));

        mvc.perform(get("/v1/leases/{lease}/financial-evidence",lease)
                        .with(token(actor,w,"PROPERTY_MANAGEMENT","oula.tenancy.finance.record"))
                        .header("X-OULA-Workspace-ID",w)
                        .header("X-OULA-Purpose","PROPERTY_MANAGEMENT"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/leases/{lease}/financial-evidence",lease)
                        .with(token(actor,w,"PROPERTY_DECISION_SUPPORT","oula.tenancy.finance.read"))
                        .header("X-OULA-Workspace-ID",w)
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
