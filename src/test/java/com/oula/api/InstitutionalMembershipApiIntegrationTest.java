package com.oula.api;

import com.oula.platform.DeterministicUuid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Institutional APIs reject forged workspace-membership JWT claims unless a trusted
 * issuer/subject/actor binding is active in PostgreSQL. Database membership revocation
 * takes effect immediately without waiting for a signed token to expire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstitutionalMembershipApiIntegrationTest {
    static final String ISSUER = "https://institution-idp.example.test";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuerBoundMembershipAndInstitutionDealRosterPreventUnauthorizedDisclosure() throws Exception {
        UUID ws=UUID.randomUUID(), originator=UUID.randomUUID(),
             recipient=UUID.randomUUID(), unrelated=UUID.randomUUID();
        UUID intent=UUID.randomUUID(), property=UUID.randomUUID();
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'ORGANIZATION','Test Agency','ACTIVE')
                """,ws);
        jdbc.update("""
                insert into intent.intent(id,workspace_id,intent_type,status,budget_max,minimum_bedrooms,preferred_districts)
                values (?,?,'BUY','ACTIVE',1500000,3,'["Malqa"]'::jsonb)
                """,intent,ws);
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,'RESIDENTIAL','Malqa',3,1300000)
                """,property,ws);

        String key="institution-create-"+UUID.randomUUID();
        UUID deal=DeterministicUuid.from("transaction.create.v1.entity",ws,key);
        String createPayload="""
                {"intentId":"%s","propertyId":"%s"}
                """.formatted(intent,property);

        // A valid JWT can assert this workspace without any server-side office roster.
        mvc.perform(post("/v1/transactions")
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.create"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",key)
                .contentType("application/json").content(createPayload))
                .andExpect(status().isForbidden());

        provision(ws,originator,ISSUER,"subject-"+originator,"ADMIN");
        mvc.perform(post("/v1/transactions")
                .with(token(originator,ws,"https://different-idp.example.test",
                        "subject-"+originator,"oula.transaction.create"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",key)
                .contentType("application/json").content(createPayload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/transactions")
                .with(token(originator,ws,ISSUER,"impersonated-subject",
                        "oula.transaction.create"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",key)
                .contentType("application/json").content(createPayload))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions")
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.create"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",key)
                .contentType("application/json").content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(deal.toString()));

        String readInvite="""
                {"actorId":"%s","expiresAt":"%s"}
                """.formatted(recipient,java.time.Instant.now().plusSeconds(86400));

        // Even a legitimate deal originator cannot invite an unprovisioned actor ID.
        mvc.perform(post("/v1/transactions/{deal}/read-grants",deal)
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","unlisted-recipient-"+UUID.randomUUID())
                .contentType("application/json").content(readInvite))
                .andExpect(status().isForbidden());

        provision(ws,recipient,ISSUER,"subject-"+recipient,"BROKER");
        mvc.perform(post("/v1/transactions/{deal}/read-grants",deal)
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","listed-recipient-"+UUID.randomUUID())
                .contentType("application/json").content(readInvite))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.accepted").value(false));

        mvc.perform(get("/v1/transactions/{deal}/deal-room",deal)
                .with(token(recipient,ws,ISSUER,"subject-"+recipient,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions/{deal}/read-grants/accept",deal)
                .with(token(recipient,ws,ISSUER,"wrong-subject",
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","bad-recipient-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        String acceptKey="accept-listed-"+UUID.randomUUID();
        mvc.perform(post("/v1/transactions/{deal}/read-grants/accept",deal)
                .with(token(recipient,ws,ISSUER,"subject-"+recipient,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));

        mvc.perform(get("/v1/transactions/{deal}/deal-room",deal)
                .with(token(recipient,ws,ISSUER,"subject-"+recipient,
                        "oula.transaction.read","oula.transaction.advance"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.id").value(deal.toString()))
                .andExpect(jsonPath("$.availableCommands.length()").value(0));

        // A revoked office membership overrides a still-valid signed JWT and
        // still-active, accepted transaction-specific viewer grant.
        jdbc.update("""
                update iam.institutional_membership
                   set status='REVOKED',revoked_at=now()
                 where workspace_id=? and actor_id=?
                """,ws,recipient);
        mvc.perform(get("/v1/transactions/{deal}/deal-room",deal)
                .with(token(recipient,ws,ISSUER,"subject-"+recipient,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/transactions/{deal}/read-grants/accept",deal)
                .with(token(recipient,ws,ISSUER,"subject-"+recipient,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isForbidden());

        // Even an otherwise authorized owner cannot re-invite an ex-member.
        mvc.perform(post("/v1/transactions/{deal}/read-grants",deal)
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.grant.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","revoked-cannot-reinvite-"+UUID.randomUUID())
                .contentType("application/json").content(readInvite))
                .andExpect(status().isForbidden());

        // Other actors with the same office ID in JWT are never members by implication.
        mvc.perform(get("/v1/transactions/{deal}/deal-room",deal)
                .with(token(unrelated,ws,ISSUER,"subject-"+unrelated,"oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/transactions/{deal}/deal-room",deal)
                .with(token(originator,ws,ISSUER,"subject-"+originator,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION"))
                .andExpect(status().isOk());
    }

    @Test
    void personalWorkspacesKeepTheirExistingJwtAccessModelAndDisabledOfficesFailClosed()
            throws Exception {
        UUID personal=UUID.randomUUID(), disabledOffice=UUID.randomUUID();
        UUID actor=UUID.randomUUID();
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'PERSONAL','Individual','ACTIVE')
                """,personal);
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'ORGANIZATION','Disabled Agency','INACTIVE')
                """,disabledOffice);
        mvc.perform(get("/v1/consents")
                .with(token(actor,personal,ISSUER,"subject-"+actor,"oula.consent.read",
                        "PROPERTY_DECISION_SUPPORT"))
                .header("X-OULA-Workspace-ID",personal)
                .header("X-OULA-Purpose","PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isOk());
        provision(disabledOffice,actor,ISSUER,"subject-"+actor,"ADMIN");
        mvc.perform(get("/v1/consents")
                .with(token(actor,disabledOffice,ISSUER,"subject-"+actor,
                        "oula.consent.read","PROPERTY_DECISION_SUPPORT"))
                .header("X-OULA-Workspace-ID",disabledOffice)
                .header("X-OULA-Purpose","PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isForbidden());
    }

    private void provision(UUID ws, UUID actor, String issuer, String subject, String role) {
        // Direct DB provisioning in integration tests ONLY. No public registration API.
        jdbc.update("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (?,?,?,?,?,'ACTIVE','TRUSTED_BOOTSTRAP')
                """,ws,actor,issuer,subject,role);
    }

    private RequestPostProcessor token(UUID actor,UUID ws,String issuer,String subject,
                                        String scope) {
        return token(actor,ws,issuer,subject,scope,"TRANSACTION_EXECUTION");
    }

    private RequestPostProcessor token(UUID actor,UUID ws,String issuer,String subject,
                                        String firstScope,String secondScope) {
        boolean personalPurpose="PROPERTY_DECISION_SUPPORT".equals(secondScope);
        String purpose=personalPurpose?secondScope:"TRANSACTION_EXECUTION";
        String[] scopes=personalPurpose?new String[]{firstScope}:new String[]{firstScope,secondScope};
        SimpleGrantedAuthority[] authorities=Arrays.stream(scopes)
                .map(s->new SimpleGrantedAuthority("SCOPE_"+s))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(token->token
                .issuer(issuer).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",List.of(ws.toString()))
                .claim("oula_purposes",List.of(purpose)))
                .authorities(authorities);
    }
}
