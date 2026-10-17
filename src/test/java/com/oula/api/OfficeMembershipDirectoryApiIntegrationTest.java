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
 * Real HTTP + PostgreSQL test for an office roster and a PRE-MEMBERSHIP
 * inbox, including legal cancellations, expiration, actor/issuer isolation,
 * admin role revocation and the absence of an implied deal grant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfficeMembershipDirectoryApiIntegrationTest {
    private static final String ISSUER="https://verified-agency-idp.example.test";
    private static final String PURPOSE="TRANSACTION_EXECUTION";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void officeDirectoryAndInviteeInboxShowOnlyActionableScopedMemberships() throws Exception {
        UUID ws=UUID.randomUUID(), foreignWs=UUID.randomUUID(),
                admin=UUID.randomUUID(), staff=UUID.randomUUID(),
                joining=UUID.randomUUID(), cancelled=UUID.randomUUID(),
                expiring=UUID.randomUUID(), foreignAdmin=UUID.randomUUID();

        office(ws);
        office(foreignWs);
        provision(ws,admin,"admin-"+admin,"ADMIN");
        provision(ws,staff,"staff-"+staff,"STAFF");
        provision(foreignWs,foreignAdmin,"foreign-"+foreignAdmin,"ADMIN");

        UUID activeInvite=invite(ws,admin,joining,"BROKER","joining");
        UUID cancelInvite=invite(ws,admin,cancelled,"STAFF","cancel");
        UUID expiryInvite=invite(ws,admin,expiring,"STAFF","expire");

        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",cancelInvite)
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","cancel-"+UUID.randomUUID()))
                .andExpect(status().isOk());

        jdbc.update("""
                update iam.institutional_member_invitation
                   set created_at=now()-interval '3 days',
                       expires_at=now()-interval '1 day'
                 where id=?
                """,expiryInvite);

        // Before acceptance, a real STAFF is in the roster; all invitees are absent.
        mvc.perform(get("/v1/offices/members")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].actorId").exists())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mvc.perform(get("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invitationId").value(activeInvite.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].actorId").value(joining.toString()));

        // No pre-existing roster, membership workspace claim, or ADMIN role needed
        // for the exact intended signed identity to discover its OWN invitation.
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(joining,ws,ISSUER,"subject-"+joining,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invitationId").value(activeInvite.toString()));

        // A cancelled invitation exists in the audit database but cannot
        // be advertised as pending to the cancelled recipient.
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(cancelled,ws,ISSUER,"subject-"+cancelled,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(expiring,ws,ISSUER,"subject-"+expiring,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // An otherwise-valid JWT with wrong issuer, actor or subject gets no
        // listing or office-wide inventory, even if it names the right workspace.
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(joining,ws,"https://another-idp.example.test",
                        "subject-"+joining,false,"oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(joining,ws,ISSUER,"faked-subject",false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(staff,ws,ISSUER,"staff-"+staff,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/v1/offices/members")
                .with(token(staff,ws,ISSUER,"staff-"+staff,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/offices/members/invitations")
                .with(token(staff,ws,ISSUER,"staff-"+staff,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/offices/members")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/offices/members/invitations")
                .with(token(foreignAdmin,foreignWs,ISSUER,"foreign-"+foreignAdmin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(joining,ws,ISSUER,"subject-"+joining,false,
                        "oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        // Joining activates roster membership; the accepted invitation
        // no longer appears in either actionable inbox.
        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",activeInvite)
                .with(token(joining,ws,ISSUER,"subject-"+joining,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","accept-"+UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/v1/offices/members/invitations/mine")
                .with(token(joining,ws,ISSUER,"subject-"+joining,false,
                        "oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/v1/offices/members")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        // Removal is immediate, and GET cannot accidentally resurrect it.
        mvc.perform(post("/v1/offices/members/{actorId}/revoke",joining)
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key","revoke-"+UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/offices/members")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("""
                select count(*) from iam.institutional_member_invitation where id=?
                """,Long.class,activeInvite)).isEqualTo(1L);
    }

    @Test
    void lostAdminAuthorityAndInactiveWorkspaceBlockDirectoryWhilePersonalClaimsCannotDiscoverOffice()
            throws Exception {
        UUID ws=UUID.randomUUID(), personal=UUID.randomUUID(), admin=UUID.randomUUID();
        office(ws);
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'PERSONAL','Private User','ACTIVE')
                """,personal);
        provision(ws,admin,"admin-"+admin,"ADMIN");

        mvc.perform(get("/v1/offices/members")
                .with(token(admin,personal,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",personal)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        jdbc.update("""
                update iam.institutional_membership set status='REVOKED',revoked_at=now()
                 where workspace_id=? and actor_id=?
                """,ws,admin);
        mvc.perform(get("/v1/offices/members")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE))
                .andExpect(status().isForbidden());
    }

    private UUID invite(UUID ws,UUID admin,UUID actor,String role,String suffix) throws Exception {
        String key="dir-"+suffix+"-"+UUID.randomUUID();
        UUID invitation=DeterministicUuid.from("office.member.invite.v1.entity",ws,key);
        String body="""
                {"actorId":"%s","issuer":"%s","subject":"subject-%s",
                 "role":"%s","expiresAt":"%s"}
                """.formatted(actor,ISSUER,actor,role,Instant.now().plusSeconds(86400));
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,ISSUER,"admin-"+admin,true,
                        "oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose",PURPOSE)
                .header("Idempotency-Key",key)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        return invitation;
    }

    private void office(UUID ws) {
        jdbc.update("""
                insert into iam.workspace(id,workspace_type,name,status)
                values (?,'ORGANIZATION','Test Office','ACTIVE')
                """,ws);
    }

    private void provision(UUID ws,UUID actor,String subject,String role) {
        jdbc.update("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (?,?,?,?,?,'ACTIVE','TRUSTED_BOOTSTRAP')
                """,ws,actor,ISSUER,subject,role);
    }

    private RequestPostProcessor token(UUID actor,UUID ws,String issuer,String subject,
                                       boolean claimWorkspace,String... scopes) {
        return jwt().jwt(j->j.issuer(issuer).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",claimWorkspace ? List.of(ws.toString()) : List.of())
                .claim("oula_purposes",List.of(PURPOSE)))
                .authorities(Arrays.stream(scopes)
                        .map(s->new SimpleGrantedAuthority("SCOPE_"+s))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
