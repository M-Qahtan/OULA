package com.oula.iam;

import com.oula.platform.outbox.OutboxWriter;
import com.oula.platform.audit.AuditWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OfficeMembershipService {
    private final JdbcClient jdbc;
    private final InstitutionalMembershipRegistry memberships;
    private final OutboxWriter outbox;
    private final AuditWriter audit;

    public OfficeMembershipService(JdbcClient jdbc, InstitutionalMembershipRegistry memberships,
                                   OutboxWriter outbox, AuditWriter audit) {
        this.jdbc=jdbc;
        this.memberships=memberships;
        this.outbox=outbox;
        this.audit=audit;
    }

    /** Used in controller before an idempotency replay. Re-invites change version. */
    @Transactional(readOnly=true)
    public long requireCurrentInvitation(UUID workspaceId,UUID actorId, String subject,URL issuer) {
        memberships.requireActiveInstitutionalWorkspace(workspaceId);
        if (issuer==null || subject==null || subject.isBlank()) {
            throw new AccessDeniedException("recipient's signed issuer/subject are required");
        }
        return jdbc.sql("""
                select version from iam.institutional_member_invitation
                 where workspace_id=:ws and actor_id=:actor
                   and issuer=:issuer and subject=:subject
                   and status in ('PENDING','ACCEPTED') and expires_at>now()
                """).param("ws",workspaceId).param("actor",actorId)
                .param("issuer",issuer.toString()).param("subject",subject)
                .query(Long.class).optional()
                .orElseThrow(()->new AccessDeniedException("valid recipient-bound invitation required"));
    }

    @Transactional
    public OfficeInvitationView invite(UUID workspaceId,UUID administrator,String adminSubject,URL issuer,
                                       UUID targetActor,String targetSubject,String role,
                                       Instant expiresAt,UUID correlationId) {
        Objects.requireNonNull(targetActor,"target actor");
        Objects.requireNonNull(expiresAt,"expiry");
        if (issuer==null || targetSubject==null || targetSubject.isBlank() || targetSubject.length()>256) {
            throw new IllegalArgumentException("recipient must have a verified IdP subject");
        }
        if (!List.of("BROKER","STAFF").contains(role)) {
            throw new IllegalArgumentException("office invitations may only assign BROKER or STAFF");
        }
        Instant now=Instant.now();
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plusSeconds(30L*86400L))) {
            throw new IllegalArgumentException("invitation expiry must be within the next 30 days");
        }
        if (administrator.equals(targetActor)) {
            throw new IllegalArgumentException("admin cannot invite self");
        }
        lockWorkspace(workspaceId);
        memberships.requireActiveOfficeAdmin(workspaceId,administrator,adminSubject,issuer);
        var boundMember=jdbc.sql("""
                select issuer,subject,status from iam.institutional_membership
                 where workspace_id=:ws and actor_id=:actor
                """).param("ws",workspaceId).param("actor",targetActor)
                .query((rs,n)->new IdentityRow(rs.getString("issuer"),rs.getString("subject"),
                        rs.getString("status"))).optional();
        if (boundMember.isPresent()) {
            var member=boundMember.get();
            if (!member.issuer().equals(issuer.toString()) || !member.subject().equals(targetSubject)) {
                throw new AccessDeniedException("actor already bound to a different identity");
            }
            if ("ACTIVE".equals(member.status())) {
                throw new IllegalStateException("actor is already an active office member");
            }
        }
        int updated=jdbc.sql("""
                insert into iam.institutional_member_invitation
                  (workspace_id,actor_id,issuer,subject,member_role,status,invited_by,expires_at)
                values (:ws,:actor,:issuer,:subject,:role,'PENDING',:invitedBy,:expires)
                on conflict (workspace_id,actor_id) do update
                   set issuer=excluded.issuer,subject=excluded.subject,
                       member_role=excluded.member_role,status='PENDING',
                       invited_by=excluded.invited_by,expires_at=excluded.expires_at,
                       version=iam.institutional_member_invitation.version+1,
                       updated_at=now(),accepted_at=null,revoked_at=null
                 where iam.institutional_member_invitation.issuer=excluded.issuer
                   and iam.institutional_member_invitation.subject=excluded.subject
                """)
                .param("ws",workspaceId).param("actor",targetActor)
                .param("issuer",issuer.toString()).param("subject",targetSubject)
                .param("role",role).param("invitedBy",administrator)
                .param("expires",OffsetDateTime.ofInstant(expiresAt,ZoneOffset.UTC))
                .update();
        if (updated!=1) throw new AccessDeniedException("invitation cannot switch an actor's identity binding");
        long version=jdbc.sql("""
                select version from iam.institutional_member_invitation
                 where workspace_id=:ws and actor_id=:actor
                """).param("ws",workspaceId).param("actor",targetActor)
                .query(Long.class).single();
        outbox.append("iam.office.member.invited.v1","OfficeMembership",targetActor,
                workspaceId,correlationId,correlationId,Map.of(
                        "actorId",targetActor,"invitedBy",administrator,
                        "role",role,"version",version));
        audit.append(workspaceId,administrator,adminSubject,AccessPurpose.INTEGRATION_OPERATIONS.name(),
                "iam.office.member.invited","OfficeMembership",targetActor,correlationId,
                Map.of("role",role,"version",version));
        return new OfficeInvitationView(workspaceId,targetActor,role,"PENDING",version,expiresAt);
    }

    /** The sole public activation route; membership is created in this atomic transaction. */
    @Transactional
    public OfficeInvitationView accept(UUID workspaceId,UUID actorId,String subject,URL issuer,
                                        UUID correlationId) {
        lockWorkspace(workspaceId);
        long version=requireCurrentInvitation(workspaceId,actorId,subject,issuer);
        var pending=jdbc.sql("""
                select member_role,expires_at from iam.institutional_member_invitation
                 where workspace_id=:ws and actor_id=:actor and issuer=:issuer
                   and subject=:subject and status='PENDING' and expires_at>now()
                """).param("ws",workspaceId).param("actor",actorId)
                .param("issuer",issuer.toString()).param("subject",subject)
                .query((rs,n)->new PendingRow(rs.getString("member_role"),
                        rs.getObject("expires_at",OffsetDateTime.class).toInstant()))
                .optional().orElseThrow(()->new IllegalStateException("invitation is no longer pending"));
        int updated=jdbc.sql("""
                insert into iam.institutional_membership
                  (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (:ws,:actor,:issuer,:subject,:role,'ACTIVE','INVITATION_ACCEPTED')
                on conflict (workspace_id,actor_id) do update
                   set member_role=excluded.member_role,status='ACTIVE',revoked_at=null
                 where iam.institutional_membership.status='REVOKED'
                   and iam.institutional_membership.issuer=excluded.issuer
                   and iam.institutional_membership.subject=excluded.subject
                """).param("ws",workspaceId).param("actor",actorId)
                .param("issuer",issuer.toString()).param("subject",subject)
                .param("role",pending.get().role()).update();
        if (updated!=1) throw new AccessDeniedException("membership already active or identity mismatch");
        int accepted=jdbc.sql("""
                update iam.institutional_member_invitation
                   set status='ACCEPTED',accepted_at=now(),updated_at=now()
                 where workspace_id=:ws and actor_id=:actor
                   and status='PENDING' and version=:version and expires_at>now()
                """).param("ws",workspaceId).param("actor",actorId)
                .param("version",version).update();
        if (accepted!=1) throw new IllegalStateException("invitation state changed");
        outbox.append("iam.office.member.accepted.v1","OfficeMembership",actorId,
                workspaceId,correlationId,correlationId,
                Map.of("actorId",actorId,"role",pending.get().role(),"version",version));
        audit.append(workspaceId,actorId,subject,AccessPurpose.INTEGRATION_OPERATIONS.name(),
                "iam.office.member.accepted","OfficeMembership",actorId,correlationId,
                Map.of("role",pending.get().role(),"version",version));
        return new OfficeInvitationView(workspaceId,actorId,pending.get().role(),
                "ACCEPTED",version,pending.get().expiresAt());
    }

    @Transactional
    public OfficeInvitationView revoke(UUID workspaceId,UUID administrator,String adminSubject,
                                        URL issuer,UUID targetActor,UUID correlationId) {
        if (administrator.equals(targetActor)) throw new AccessDeniedException("cannot revoke self");
        lockWorkspace(workspaceId);
        memberships.requireActiveOfficeAdmin(workspaceId,administrator,adminSubject,issuer);
        // Admins can be provisioned or revoked only by a stronger trusted authority.
        Long adminCount=jdbc.sql("""
                select count(*) from iam.institutional_membership
                 where workspace_id=:ws and actor_id=:actor and member_role='ADMIN'
                """).param("ws",workspaceId).param("actor",targetActor).query(Long.class).single();
        if(adminCount!=null && adminCount>0) {
            throw new AccessDeniedException("office admin membership requires trusted operator process");
        }
        int membership=jdbc.sql("""
                update iam.institutional_membership
                   set status='REVOKED',revoked_at=now()
                 where workspace_id=:ws and actor_id=:actor
                   and member_role in ('BROKER','STAFF') and status='ACTIVE'
                """).param("ws",workspaceId).param("actor",targetActor).update();
        int invitation=jdbc.sql("""
                update iam.institutional_member_invitation
                   set status='REVOKED',revoked_at=now(),updated_at=now()
                 where workspace_id=:ws and actor_id=:actor
                   and status in ('PENDING','ACCEPTED')
                """).param("ws",workspaceId).param("actor",targetActor).update();
        if(membership!=1 && invitation!=1) {
            throw new java.util.NoSuchElementException("pending invitation or active member not found");
        }
        outbox.append("iam.office.member.revoked.v1","OfficeMembership",targetActor,
                workspaceId,correlationId,correlationId,
                Map.of("actorId",targetActor,"revokedBy",administrator));
        audit.append(workspaceId,administrator,adminSubject,AccessPurpose.INTEGRATION_OPERATIONS.name(),
                "iam.office.member.revoked","OfficeMembership",targetActor,correlationId,
                Map.of("memberRevoked",membership==1,"invitationRevoked",invitation==1));
        return new OfficeInvitationView(workspaceId,targetActor,"","REVOKED",0,null);
    }

    @Transactional(readOnly=true)
    public List<OfficeMemberView> members(UUID workspaceId,UUID administrator,String subject,URL issuer) {
        memberships.requireActiveOfficeAdmin(workspaceId,administrator,subject,issuer);
        return jdbc.sql("""
                select actor_id,subject,member_role,status
                  from iam.institutional_membership
                 where workspace_id=:ws
                 order by created_at,actor_id
                """).param("ws",workspaceId)
                .query((rs,n)->new OfficeMemberView(
                        rs.getObject("actor_id",UUID.class),rs.getString("subject"),
                        rs.getString("member_role"),rs.getString("status"))).list();
    }

    private void lockWorkspace(UUID workspaceId) {
        boolean found=jdbc.sql("""
                select id from iam.workspace
                 where id=:ws and status='ACTIVE' and workspace_type <> 'PERSONAL'
                 for update
                """).param("ws",workspaceId).query(UUID.class).optional().isPresent();
        if (!found) throw new AccessDeniedException("active institutional workspace required");
    }

    private record IdentityRow(String issuer,String subject,String status) {}
    private record PendingRow(String role,Instant expiresAt) {}
}
