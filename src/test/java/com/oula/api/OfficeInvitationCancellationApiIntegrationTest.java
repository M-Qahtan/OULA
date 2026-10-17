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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfficeInvitationCancellationApiIntegrationTest {
    private static final String ISSUER="https://agency-idp.example.test";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pendingInvitationCancellationPreventsAcceptanceAndPreservesEvidence() throws Exception {
        UUID ws=UUID.randomUUID(), admin=UUID.randomUUID(), recipient=UUID.randomUUID();
        UUID staff=UUID.randomUUID();
        office(ws);
        provision(ws,admin,"admin-"+admin,"ADMIN");
        provision(ws,staff,"staff-"+staff,"STAFF");
        String inviteKey="invite-cancellable-"+UUID.randomUUID();
        UUID invite=DeterministicUuid.from("office.member.invite.v1.entity",ws,inviteKey);
        String payload=payload(recipient);
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",inviteKey)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // A scope on a real STAFF record is NOT enough for cancelling.
        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(staff,ws,"staff-"+staff,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","cancel-staff-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(admin,ws,"admin-"+admin,"oula.transaction.read"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","cancel-wrong-scope-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        String cancelKey="cancel-once-"+UUID.randomUUID();
        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",cancelKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.invitationId").value(invite.toString()))
                .andExpect(header().string("Idempotency-Replayed","false"));

        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",cancelKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed","true"));

        assertThat(count("select count(*) from iam.institutional_invitation_cancellation where invitation_id=?",invite))
                .isEqualTo(1);
        // Preserve original invite and actor provenance, rather than deleting.
        assertThat(count("select count(*) from iam.institutional_member_invitation where id=? and status='PENDING'",invite))
                .isEqualTo(1);

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",invite)
                .with(token(recipient,ws,"subject-"+recipient,"oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","cannot-accept-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());

        assertThat(count("select count(*) from iam.institutional_membership where workspace_id=? and actor_id=?",ws,recipient))
                .isZero();
        assertThat(count("select count(*) from platform.audit_log where workspace_id=? and action='iam.office.invitation.cancelled'",ws))
                .isEqualTo(1);
        assertThat(count("select count(*) from platform.outbox_event where workspace_id=? and event_type='iam.office.invitation.cancelled.v1'",ws))
                .isEqualTo(1);

        // No extra cancellations may be recorded with a new idempotency key.
        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","cancel-twice-"+UUID.randomUUID()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledInvitationCannotCrossTenantAndAcceptedMembershipCannotBeCancelled()
            throws Exception {
        UUID ws=UUID.randomUUID(), foreignWs=UUID.randomUUID();
        UUID admin=UUID.randomUUID(), foreignAdmin=UUID.randomUUID(), recipient=UUID.randomUUID();
        office(ws);office(foreignWs);
        provision(ws,admin,"admin-"+admin,"ADMIN");
        provision(foreignWs,foreignAdmin,"other-"+foreignAdmin,"ADMIN");

        String key="same-tenant-"+UUID.randomUUID();
        UUID invite=DeterministicUuid.from("office.member.invite.v1.entity",ws,key);
        mvc.perform(post("/v1/offices/members/invitations")
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key",key)
                .contentType("application/json").content(payload(recipient)))
                .andExpect(status().isCreated());

        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(foreignAdmin,foreignWs,"other-"+foreignAdmin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",foreignWs)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","foreign-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from iam.institutional_invitation_cancellation where invitation_id=?",invite))
                .isZero();

        mvc.perform(post("/v1/offices/members/invitations/{id}/accept",invite)
                .with(token(recipient,ws,"subject-"+recipient,"oula.office.membership.accept"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","valid-accept-"+UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/offices/members/invitations/{id}/cancel",invite)
                .with(token(admin,ws,"admin-"+admin,"oula.office.members.manage"))
                .header("X-OULA-Workspace-ID",ws)
                .header("X-OULA-Purpose","TRANSACTION_EXECUTION")
                .header("Idempotency-Key","accepted-not-cancellable-"+UUID.randomUUID()))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from iam.institutional_membership where workspace_id=? and actor_id=? and status='ACTIVE'",ws,recipient))
                .isEqualTo(1);
    }

    private void office(UUID ws) {
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'ORGANIZATION','Office','ACTIVE')",ws);
    }
    private void provision(UUID ws,UUID actor,String subject,String role) {
        jdbc.update("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (?,?,?,?,?,'ACTIVE','TRUSTED_BOOTSTRAP')
                """,ws,actor,ISSUER,subject,role);
    }
    private String payload(UUID actor) {
        return """
                {"actorId":"%s","issuer":"%s","subject":"subject-%s",
                 "role":"BROKER","expiresAt":"%s"}
                """.formatted(actor,ISSUER,actor,Instant.now().plusSeconds(3600));
    }
    private long count(String sql,Object...args) {
        return jdbc.queryForObject(sql,Long.class,args);
    }
    private RequestPostProcessor token(UUID actor,UUID ws,String subject,String... scopes) {
        return jwt().jwt(j->j.issuer(ISSUER).subject(subject)
                .claim("actor_id",actor.toString())
                .claim("oula_workspace_ids",List.of(ws.toString()))
                .claim("oula_purposes",List.of("TRANSACTION_EXECUTION")))
                .authorities(java.util.Arrays.stream(scopes)
                        .map(s->new SimpleGrantedAuthority("SCOPE_"+s))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
