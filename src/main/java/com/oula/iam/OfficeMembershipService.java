package com.oula.iam;

import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Private institution admission is a two-party handshake:
 * externally trusted ADMIN invitation -> intended JWT identity's acceptance.
 * The JWT's claimed workspace IDs are explicitly NOT proof of membership.
 * No endpoint here can create or reinstate an ADMIN.
 */
@Service
public class OfficeMembershipService {
    private final JdbcClient jdbc;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public OfficeMembershipService(JdbcClient jdbc, AuditWriter audit, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly=true)
    public void requireAdmin(AccessContext admin) {
        long count=jdbc.sql("""
                select count(*) from iam.institutional_membership m
                 join iam.workspace w on w.id=m.workspace_id
                 where m.workspace_id=:ws and m.actor_id=:actor
                   and m.member_role='ADMIN' and m.status='ACTIVE'
                   and w.workspace_type<>'PERSONAL' and w.status='ACTIVE'
                """).param("ws",admin.workspaceId()).param("actor",admin.actorId())
                .query(Long.class).single();
        if(count!=1) throw new AccessDeniedException("active institutional ADMIN required");
    }

    @Transactional
    public OfficeMemberInvitationView invite(
            AccessContext admin, UUID actorId, String issuer, String subject,
            String role, Instant expiresAt, UUID invitationId, UUID correlationId) {
        requireAdmin(admin);
        if (!List.of("BROKER","STAFF").contains(role)) {
            throw new IllegalArgumentException("only BROKER or STAFF can be invited");
        }
        if (issuer==null || issuer.isBlank() || issuer.length()>512 ||
                subject==null || subject.isBlank() || subject.length()>256) {
            throw new IllegalArgumentException("recipient issuer and subject are required");
        }
        if (actorId == null || actorId.equals(admin.actorId())) {
            throw new IllegalArgumentException("a different recipient actor is required");
        }
        Instant now=Instant.now();
        if (expiresAt==null || !expiresAt.isAfter(now) ||
                expiresAt.isAfter(now.plusSeconds(14L*86400L))) {
            throw new IllegalArgumentException("office invitation must expire within 14 days");
        }
        long existing=jdbc.sql("""
                select count(*) from iam.institutional_membership
                 where workspace_id=:ws and actor_id=:actor
                """).param("ws",admin.workspaceId()).param("actor",actorId)
                .query(Long.class).single();
        if(existing>0) throw new IllegalStateException(
                "existing or revoked members require trusted offboarding review, not re-invitation");

        jdbc.sql("""
                insert into iam.institutional_member_invitation
                  (id,workspace_id,actor_id,issuer,subject,member_role,status,
                   invited_by,expires_at)
                values (:id,:ws,:actor,:issuer,:subject,:role,'PENDING',:by,:expiry)
                """).param("id",invitationId).param("ws",admin.workspaceId())
                .param("actor",actorId).param("issuer",issuer).param("subject",subject)
                .param("role",role).param("by",admin.actorId())
                .param("expiry",OffsetDateTime.ofInstant(expiresAt,ZoneOffset.UTC))
                .update();
        audit.append(admin.workspaceId(),admin.actorId(),admin.subject(),
                admin.purpose().name(),"iam.office.invitation.created","InstitutionInvitation",
                invitationId,correlationId,Map.of("actorId",actorId,"role",role));
        outbox.append("iam.office.invitation.created.v1","InstitutionInvitation",
                invitationId,admin.workspaceId(),correlationId,correlationId,
                Map.of("actorId",actorId,"role",role,"expiresAt",expiresAt.toString()));
        return new OfficeMemberInvitationView(
                invitationId,admin.workspaceId(),actorId,role,"PENDING",expiresAt);
    }

    /** No office membership, workspace JWT claim, or office-manager permission required
     * for a recipient to accept their own invitation. Resource-server JWT validity,
     * purpose and exact acceptance scope are necessary. Identity is matched to
     * the immutable invitation before any response replay or mutation. */
    public RecipientIdentity recipientIdentity(Authentication authentication, String purpose) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) ||
                !jwt.isAuthenticated() || !"TRANSACTION_EXECUTION".equals(purpose)) {
            throw new AccessDeniedException("authenticated invitation acceptance purpose required");
        }
        Object purposeClaim=jwt.getToken().getClaim("oula_purposes");
        if (!(purposeClaim instanceof Collection<?> ps) ||
                !ps.contains("TRANSACTION_EXECUTION")) {
            throw new AccessDeniedException("invitation acceptance purpose claim missing");
        }
        boolean scope=jwt.getAuthorities().stream().anyMatch(a->
                "SCOPE_oula.office.membership.accept".equals(a.getAuthority()));
        if(!scope) throw new AccessDeniedException("invitation acceptance scope missing");
        URL issuer=jwt.getToken().getIssuer();
        String subject=jwt.getToken().getSubject();
        Object actor=jwt.getToken().getClaim("actor_id");
        if(issuer==null||subject==null||subject.isBlank()||actor==null) {
            throw new AccessDeniedException("signed recipient issuer, subject and actor required");
        }
        UUID actorId;
        try { actorId=UUID.fromString(actor.toString()); }
        catch(IllegalArgumentException ex) {
            throw new AccessDeniedException("invalid actor_id");
        }
        return new RecipientIdentity(actorId,issuer.toString(),subject);
    }

    /**
     * Read authorization guard also runs BEFORE idempotency lookup. Revoked
     * memberships cannot resurrect successful accepted-invitation receipts.
     */
    @Transactional(readOnly=true)
    public void requireInvitationForRecipient(UUID workspace, UUID id, RecipientIdentity recipient) {
        Long count=jdbc.sql("""
                select count(*) from iam.institutional_member_invitation i
                 join iam.workspace w on w.id=i.workspace_id
                 where i.id=:id and i.workspace_id=:ws and i.actor_id=:actor
                   and i.issuer=:issuer and i.subject=:subject
                   and w.workspace_type<>'PERSONAL' and w.status='ACTIVE'
                   and not exists (
                       select 1 from iam.institutional_invitation_cancellation c
                        where c.invitation_id=i.id
                   )
                   and ((i.status='PENDING' and i.expires_at>now())
                        or (i.status='ACCEPTED' and exists(
                            select 1 from iam.institutional_membership m
                             where m.workspace_id=i.workspace_id
                               and m.actor_id=i.actor_id and m.issuer=i.issuer
                               and m.subject=i.subject and m.status='ACTIVE')))
                """).param("id",id).param("ws",workspace).param("actor",recipient.actorId())
                .param("issuer",recipient.issuer()).param("subject",recipient.subject())
                .query(Long.class).single();
        if(count==null||count!=1) throw new AccessDeniedException(
                "invitation unavailable to signed recipient");
    }

    @Transactional
    public OfficeMembershipView accept(UUID workspace, UUID invitationId,
                                       RecipientIdentity recipient, UUID correlationId) {
        var pending=jdbc.sql("""
                select actor_id, issuer, subject, member_role from iam.institutional_member_invitation
                 where id=:id and workspace_id=:ws and status='PENDING'
                   and expires_at>now()
                   and not exists (
                       select 1 from iam.institutional_invitation_cancellation c
                        where c.invitation_id=iam.institutional_member_invitation.id
                   ) for update
                """).param("id",invitationId).param("ws",workspace)
                .query((rs,n)->new PendingInvitation(
                        rs.getObject("actor_id",UUID.class),
                        rs.getString("issuer"),rs.getString("subject"),rs.getString("member_role")))
                .optional().orElseThrow(()->new IllegalStateException(
                        "invitation already accepted, expired, or unavailable"));
        if(!pending.actorId().equals(recipient.actorId()) ||
                !pending.issuer().equals(recipient.issuer()) ||
                !pending.subject().equals(recipient.subject())) {
            throw new AccessDeniedException("invitation belongs to another identity");
        }
        jdbc.sql("""
                insert into iam.institutional_membership
                    (workspace_id,actor_id,issuer,subject,member_role,status,provisioning_source)
                values (:ws,:actor,:issuer,:subject,:role,'ACTIVE','ADMIN_INVITATION')
                """).param("ws",workspace).param("actor",recipient.actorId())
                .param("issuer",recipient.issuer()).param("subject",recipient.subject())
                .param("role",pending.role()).update();
        jdbc.sql("""
                update iam.institutional_member_invitation
                   set status='ACCEPTED', accepted_at=now()
                 where id=:id and status='PENDING'
                """).param("id",invitationId).update();
        audit.append(workspace,recipient.actorId(),recipient.subject(),
                "TRANSACTION_EXECUTION","iam.office.invitation.accepted","InstitutionInvitation",
                invitationId,correlationId,Map.of("role",pending.role()));
        outbox.append("iam.office.invitation.accepted.v1","InstitutionInvitation",
                invitationId,workspace,correlationId,correlationId,
                Map.of("actorId",recipient.actorId(),"role",pending.role()));
        return new OfficeMembershipView(workspace,recipient.actorId(),pending.role(),"ACTIVE");
    }

    /**
     * An administrator may withdraw a still-pending invitation before recipient
     * acceptance. The row is retained for evidence; a separate immutable
     * cancellation row blocks both fresh acceptance and cached receipts.
     * Invitation row locks serialize the acceptance/cancellation race.
     */
    @Transactional
    public OfficeMemberInvitationView cancelInvitation(
            AccessContext admin, UUID invitationId, UUID correlationId) {
        requireAdmin(admin);
        var invite=jdbc.sql("""
                select actor_id,member_role,expires_at
                  from iam.institutional_member_invitation
                 where id=:id and workspace_id=:ws and status='PENDING'
                 for update
                """).param("id",invitationId).param("ws",admin.workspaceId())
                .query((rs,n)->new PendingCancellation(
                        rs.getObject("actor_id",UUID.class),
                        rs.getString("member_role"),
                        rs.getObject("expires_at",OffsetDateTime.class).toInstant()))
                .optional().orElseThrow(()->new AccessDeniedException(
                        "only pending invitations in this office may be cancelled"));
        int inserted=jdbc.sql("""
                insert into iam.institutional_invitation_cancellation
                    (invitation_id,workspace_id,cancelled_by)
                values (:id,:ws,:admin)
                on conflict (invitation_id) do nothing
                """).param("id",invitationId).param("ws",admin.workspaceId())
                .param("admin",admin.actorId()).update();
        if(inserted!=1) throw new IllegalStateException("invitation already cancelled");
        audit.append(admin.workspaceId(),admin.actorId(),admin.subject(),
                admin.purpose().name(),"iam.office.invitation.cancelled",
                "InstitutionInvitation",invitationId,correlationId,
                Map.of("actorId",invite.actorId(),"reasonCode","ADMIN_WITHDRAWAL"));
        outbox.append("iam.office.invitation.cancelled.v1","InstitutionInvitation",
                invitationId,admin.workspaceId(),correlationId,correlationId,
                Map.of("actorId",invite.actorId(),"cancelledBy",admin.actorId()));
        return new OfficeMemberInvitationView(invitationId,admin.workspaceId(),
                invite.actorId(),invite.role(),"CANCELLED",invite.expiresAt());
    }

    /** No admin self-revocation or manager-based privilege changes in this slice. */
    @Transactional
    public OfficeMembershipView revoke(AccessContext admin, UUID target, UUID correlationId) {
        requireAdmin(admin);
        if(admin.actorId().equals(target))
            throw new AccessDeniedException("administrators cannot revoke themselves");
        var member=jdbc.sql("""
                select member_role from iam.institutional_membership
                 where workspace_id=:ws and actor_id=:actor and status='ACTIVE'
                   and member_role in ('STAFF','BROKER') for update
                """).param("ws",admin.workspaceId()).param("actor",target)
                .query(String.class).optional().orElseThrow(()->
                        new AccessDeniedException("only active STAFF or BROKER may be revoked"));
        jdbc.sql("""
                update iam.institutional_membership
                   set status='REVOKED',revoked_at=now()
                 where workspace_id=:ws and actor_id=:actor
                """).param("ws",admin.workspaceId()).param("actor",target).update();
        audit.append(admin.workspaceId(),admin.actorId(),admin.subject(),
                admin.purpose().name(),"iam.office.member.revoked","InstitutionMember",
                target,correlationId,Map.of("role",member,"actorId",target));
        outbox.append("iam.office.member.revoked.v1","InstitutionMember",
                target,admin.workspaceId(),correlationId,correlationId,
                Map.of("actorId",target,"role",member,"revokedBy",admin.actorId()));
        return new OfficeMembershipView(admin.workspaceId(),target,member,"REVOKED");
    }

    public record RecipientIdentity(UUID actorId,String issuer,String subject) {}
    private record PendingInvitation(UUID actorId,String issuer,String subject,String role) {}
    private record PendingCancellation(UUID actorId,String role,Instant expiresAt) {}
}
