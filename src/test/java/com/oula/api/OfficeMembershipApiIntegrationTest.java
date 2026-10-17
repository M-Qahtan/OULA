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
 * Live Spring MVC + PostgreSQL regression suite. JWT identities in this test are
 * synthetic, but the membership/invitation state machine runs against real SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfficeMembershipApiIntegrationTest {
    private static final String ISSUER="https://institution.example.test";
    private static final String PURPOSE="INTEGRATION_OPERATIONS";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void inviteAcceptListRevokeAndReplayAreBoundToOfficeAdminAndRecipientIdentity() throws Exception {
        UUID ws=office(), admin=UUID.randomUUID(), staff=UUID.randomUUID(),
             unrelated=UUID.randomUUID();
        String adminSubject="admin-"+admin, staffSubject="employee-"+staff;
        provision(ws,admin,adminSubject,"ADMIN");
        String body=payload(staff,staffSubject,"BROKER");
        String inviteKey="office-invite-"+UUID.randomUUID();

        // Unprovisioned actor and non-admin employee cannot administer the office.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,unrelated,"other","oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey).contentType("application/json")
                .content(body)).andExpect(status().isForbidden());

        String subject="admin-"+admin;
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,subject,"oula.office.members.read"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","no-scope-"+UUID.randomUUID())
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        // A matching ADMIN identity may create exactly one pending invitation.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,adminSubject,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey).contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string("Idempotency-Replayed","false"));

        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,adminSubject,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey).contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed","true"));
        assertThat(count("select count(*) from iam.institutional_member_invitation where workspace_id=?",ws))
                .isEqualTo(1L);
        assertThat(count("select count(*) from iam.institutional_membership where workspace_id=? and actor_id=?",ws,staff))
                .isZero();

        // Actor, issuer and subject must match; mere workspace JWT claim is not enough.
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,unrelated,"other","oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-actor-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,"different-subject","oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-subject-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"https://other-provider.example.test",
                        "oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","wrong-issuer-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/offices/members")
                .with(token(ws,staff,staffSubject,"oula.office.members.read"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        String acceptKey="office-accept-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(header().string("Idempotency-Replayed","false"));
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed","true"));

        assertThat(count("select count(*) from iam.institutional_membership where workspace_id=? and actor_id=? and status='ACTIVE' and provisioning_source='INVITATION_ACCEPTED'",ws,staff))
                .isEqualTo(1L);
        mvc.perform(get("/v1/offices/members")
                .with(token(ws,staff,staffSubject,"oula.office.members.read"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/offices/members")
                .with(token(ws,admin,adminSubject,"oula.office.members.read"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Staff cannot become an administrator by reusing the administrator's scope.
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,staff,staffSubject,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",inviteKey)
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        String revokeKey="office-revoke-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/{actorId}/revoke",staff)
                .with(token(ws,admin,adminSubject,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",revokeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        assertThat(count("select count(*) from iam.institutional_membership where workspace_id=? and actor_id=? and status='REVOKED'",ws,staff))
                .isEqualTo(1L);
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/consents")
                .with(token(ws,staff,staffSubject,"oula.consent.read"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        String reinviteKey="office-reinvite-"+UUID.randomUUID();
        String reinvite=payload(staff,staffSubject,"STAFF");
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,adminSubject,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",reinviteKey).contentType("application/json")
                .content(reinvite))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",acceptKey))
                .andExpect(status().isConflict());
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,staff,staffSubject,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","fresh-accept-"+UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(count("select count(*) from platform.outbox_event where event_type like 'iam.office.member.%' and workspace_id=?",ws))
                .isGreaterThanOrEqualTo(5L);
        assertThat(count("select count(*) from platform.audit_log where action like 'iam.office.member.%' and workspace_id=?",ws))
                .isGreaterThanOrEqualTo(5L);
    }

    @Test
    void pendingRevocationAdminProtectionExpiredInvitationAndPersonalIsolation() throws Exception {
        UUID ws=office(),admin=UUID.randomUUID(),recipient=UUID.randomUUID();
        provision(ws,admin,"admin-"+admin,"ADMIN");
        String body=payload(recipient,"employee-"+recipient,"STAFF");
        String key="pending-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",key).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/offices/members/{actorId}/revoke",recipient)
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","pending-revoke-"+UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,recipient,"employee-"+recipient,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","revoked-invite-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/offices/members/{actorId}/revoke",admin)
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","self-revoke-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        UUID secondAdmin=UUID.randomUUID();
        provision(ws,secondAdmin,"admin-"+secondAdmin,"ADMIN");
        mvc.perform(post("/v1/offices/members/{actorId}/revoke",secondAdmin)
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","other-admin-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","admin-escalation-"+UUID.randomUUID())
                .contentType("application/json")
                .content(payload(UUID.randomUUID(),"random-subject","ADMIN")))
                .andExpect(status().isBadRequest());

        UUID personal=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Individual','ACTIVE')",personal);
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(personal,recipient,"employee-"+recipient,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",personal).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","personal-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        // Change stored expiry to a past value: signed recipient cannot accept.
        UUID future=UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(ws,admin,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","expiry-setup-"+UUID.randomUUID())
                .contentType("application/json").content(payload(future,"future-"+future,"BROKER")))
                .andExpect(status().isCreated());
        jdbc.update("update iam.institutional_member_invitation set expires_at=now()-interval '1 day' where workspace_id=? and actor_id=?",ws,future);
        mvc.perform(post("/v1/offices/members/invitations/accept")
                .with(token(ws,future,"future-"+future,"oula.office.members.accept"))
                .header("X-OULA-Workspace-ID",ws).header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","expired-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private UUID office() {
        UUID ws=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'ORGANIZATION','Test Office','ACTIVE')",ws);
        return ws;
    }
    private void provision(UUID ws,UUID actor,String subject,String role) {
        jdbc.update("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (?,?,?,?,?,'ACTIVE','TRUSTED_BOOTSTRAP')
                """,ws,actor,ISSUER,subject,role);
    }
    private String payload(UUID actor,String subject,String role) {
        return """
                {"actorId":"%s","subject":"%s","role":"%s","expiresAt":"%s"}
                """.formatted(actor,subject,role,Instant.now().plusSeconds(86400));
    }
    private long count(String sql,Object...args) {
        return jdbc.queryForObject(sql,Long.class,args);
    }
    private RequestPostProcessor token(UUID ws,UUID actor,String subject,String... scopes) {
        return token(ws,actor,subject,ISSUER,scopes);
    }
    private RequestPostProcessor token(UUID ws,UUID actor,String subject,String issuer,String... scopes) {
        return jwt().jwt(j->j.issuer(issuer).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",List.of(ws.toString()))
                .claim("oula_purposes",List.of(PURPOSE)))
                .authorities(Arrays.stream(scopes)
                        .map(s->new SimpleGrantedAuthority("SCOPE_"+s)).toArray(SimpleGrantedAuthority[]::new));
    }
}
