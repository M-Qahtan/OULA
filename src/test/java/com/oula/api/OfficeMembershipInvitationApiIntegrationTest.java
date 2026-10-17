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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL integration gate for office-admin invitations, immutable
 * issuer/subject/actor recipient binding and coordinated offboarding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfficeMembershipInvitationApiIntegrationTest {
    private static final String ISSUER = "https://idp.o-office.example.test";
    private static final String PURPOSE = "TRANSACTION_EXECUTION";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void officeAdminInviteRecipientAcceptRevokeIsAuditedAndDealsAreInvalidated() throws Exception {
        UUID ws=UUID.randomUUID(), admin=UUID.randomUUID(), broker=UUID.randomUUID();
        UUID stranger=UUID.randomUUID(), staff=UUID.randomUUID();
        UUID deal=UUID.randomUUID();
        office(ws);
        provision(ws,admin,"subject-"+admin,"ADMIN");

        String inviteKey="invite-office-"+UUID.randomUUID();
        String payload="""
                {"actorId":"%s","issuer":"%s","subject":"subject-%s",
                 "role":"BROKER","expiresAt":"%s"}
                """.formatted(broker,ISSUER,broker,Instant.now().plusSeconds(86400L*4));

        // Signed JWT alone never elevates a nonmember to institutional ADMIN.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(stranger,ws,ISSUER,"subject-"+stranger,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","fake-admin")
                .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());

        // A JWT with another issuer is not the trusted ADMIN identity.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,"https://other.example.test","subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-issuer")
                .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());

        // No direct admin creation or privilege escalation through invitations.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","no-admin-invite")
                .contentType("application/json")
                .content(payload.replace("\"BROKER\"","\"ADMIN\"")))
                .andExpect(status().isBadRequest());

        String inviteResult=mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string("Idempotency-Replayed","false"))
                .andReturn().getResponse().getContentAsString();

        String id=java.util.regex.Pattern.compile("\"invitationId\":\"([^\"]+)\"")
                .matcher(inviteResult).results().findFirst().orElseThrow().group(1);

        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));

        assertThat(count("""
                select count(*) from iam.institutional_membership
                 where workspace_id=? and actor_id=?
                """,ws,broker)).isZero();

        // Recipient does NOT need to self-assert an already-active workspace
        // in JWT before accepting the admin's invitation.
        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"subject-"+broker,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","missing-purpose")
                .with(tokenNoPurpose(broker,ISSUER,"subject-"+broker)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(stranger,ws,ISSUER,"subject-"+stranger,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-actor"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,"https://different-idp.example.test",
                        "subject-"+broker,false,"oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-recipient-issuer"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"wrong-subject",false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-recipient-subject"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"subject-"+broker,false,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","missing-accept-scope"))
                .andExpect(status().isForbidden());

        String acceptKey="accept-office-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"subject-"+broker,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(header().string("Idempotency-Replayed","false"));

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"subject-"+broker,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed","true"));

        assertThat(count("""
                select count(*) from iam.institutional_membership
                 where workspace_id=? and actor_id=? and status='ACTIVE'
                   and provisioning_source='ADMIN_INVITATION'
                """,ws,broker)).isEqualTo(1L);

        mvc.perform(get("/v1/consents")
                .with(token(broker,ws,ISSUER,"subject-"+broker,true,
                        "oula.consent.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk());

        // An unrelated staff account cannot revoke a member even with the
        // same OAuth management scope, unless trusted as institutional ADMIN.
        provision(ws,staff,"subject-"+staff,"STAFF");
        mvc.perform(post("/v1/offices/members/{id}/revoke",broker)
                .with(token(staff,ws,ISSUER,"subject-"+staff,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","staff-cannot-revoke"))
                .andExpect(status().isForbidden());

        // A revoked employee's accepted transaction-level VIEWER grants are
        // invalidated in the SAME database transaction as membership removal.
        jdbc.update("""
                insert into tx.transaction(id,workspace_id,stage,status)
                values (?,?,'DRAFT','OPEN')
                """,deal,ws);
        jdbc.update("""
                insert into tx.actor_deal_grant
                  (transaction_id,workspace_id,actor_id,role,active,granted_by,expires_at,accepted_at)
                values (?,?,?,'VIEWER',true,?,now()+interval '1 day',now())
                """,deal,ws,broker,admin);
        mvc.perform(get("/v1/transactions/{id}/deal-room",deal)
                .with(token(broker,ws,ISSUER,"subject-"+broker,true,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/offices/members/{id}/revoke",admin)
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","cannot-revoke-admin"))
                .andExpect(status().isForbidden());

        String revokeKey="offboard-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/{id}/revoke",broker)
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",revokeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(header().string("Idempotency-Replayed","false"));

        mvc.perform(post("/v1/offices/members/{id}/revoke",broker)
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",revokeKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed","true"));

        assertThat(count("""
                select count(*) from iam.institutional_membership
                 where workspace_id=? and actor_id=? and status='REVOKED'
                """,ws,broker)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from tx.actor_deal_grant
                 where workspace_id=? and actor_id=? and active=true
                """,ws,broker)).isZero();
        assertThat(count("""
                select count(*) from platform.audit_log
                 where workspace_id=? and action='iam.office.member.revoked'
                """,ws)).isEqualTo(1L);
        assertThat(count("""
                select count(*) from platform.outbox_event
                 where workspace_id=? and event_type='transaction.access.read_revoked.v1'
                """,ws)).isEqualTo(1L);

        mvc.perform(get("/v1/transactions/{id}/deal-room",deal)
                .with(token(broker,ws,ISSUER,"subject-"+broker,true,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(broker,ws,ISSUER,"subject-"+broker,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","revoked-reinvite")
                .contentType("application/json").content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredInvitationNeverSelfRegistersAndPersonalWorkspaceCannotBeOfficeAdmin()
            throws Exception {
        UUID ws=UUID.randomUUID(), personal=UUID.randomUUID();
        UUID admin=UUID.randomUUID(), actor=UUID.randomUUID();
        office(ws);
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'PERSONAL','Individual','ACTIVE')
                """,personal);
        provision(ws,admin,"subject-"+admin,"ADMIN");
        String body="""
                {"actorId":"%s","issuer":"%s","subject":"subject-%s",
                 "role":"STAFF","expiresAt":"%s"}
                """.formatted(actor,ISSUER,actor,Instant.now().plusSeconds(3600));
        String key="expire-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(actor,personal,ISSUER,"subject-"+actor,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",personal)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","personal-unsafe")
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        String returned=mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"subject-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",key)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id=java.util.regex.Pattern.compile("\"invitationId\":\"([^\"]+)\"")
                .matcher(returned).results().findFirst().orElseThrow().group(1);
        jdbc.update("""
                update iam.institutional_member_invitation
                   set expires_at=now()-interval '1 day',
                       created_at=now()-interval '3 days'
                 where id=?
                """,UUID.fromString(id));
        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",id)
                .with(token(actor,ws,ISSUER,"subject-"+actor,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","expired-accept"))
                .andExpect(status().isForbidden());
        assertThat(count("""
                select count(*) from iam.institutional_membership
                 where workspace_id=? and actor_id=?
                """,ws,actor)).isZero();
    }

    private void office(UUID ws) {
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'ORGANIZATION','Test Brokerage Office','ACTIVE')
                """,ws);
    }
    private void provision(UUID ws, UUID actor, String subject, String role) {
        jdbc.update("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (?,?,?,?,?,'ACTIVE','TRUSTED_BOOTSTRAP')
                """,ws,actor,ISSUER,subject,role);
    }
    private Long count(String query,Object... args) {
        return jdbc.queryForObject(query,Long.class,args);
    }
    private RequestPostProcessor token(UUID actor,UUID ws,String issuer,String subject,
                                       boolean withWorkspaceClaim,String... scopes) {
        var authorities=Arrays.stream(scopes).map(s->new SimpleGrantedAuthority("SCOPE_"+s))
                .toList();
        return jwt().jwt(j->j.issuer(issuer).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",withWorkspaceClaim?
                        List.of(ws.toString()):List.of())
                .claim("oula_purposes",List.of(PURPOSE))).authorities(authorities);
    }
    private RequestPostProcessor tokenNoPurpose(UUID actor,String issuer,String subject) {
        return jwt().jwt(j->j.issuer(issuer).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",List.of())
                .claim("oula_purposes",List.of()))
                .authorities(new SimpleGrantedAuthority("SCOPE_oula.office.membership.accept"));
    }
}
