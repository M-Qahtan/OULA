package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.OfficeMemberInvitationView;
import com.oula.iam.OfficeMembershipService;
import com.oula.iam.OfficeMembershipView;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.transaction.TransactionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Office membership is governed separately from private-deal grants:
 * ADMIN invites non-admin roles; recipient must accept with its own JWT.
 * There is intentionally no public workspace/admin provisioning API.
 */
@RestController
@RequestMapping("/v1/offices/members")
class OfficeMembershipController {
    private static final String INVITE = "office.member.invite.v1";
    private static final String ACCEPT = "office.member.accept.v1";
    private static final String REVOKE = "office.member.revoke.v1";
    private static final String CANCEL = "office.member.invitation.cancel.v1";
    private final WorkspacePurposeAuthorizer authorizer;
    private final OfficeMembershipService members;
    private final TransactionApplicationService transactions;
    private final IdempotencyService idempotency;

    OfficeMembershipController(WorkspacePurposeAuthorizer authorizer,
                               OfficeMembershipService members,
                               TransactionApplicationService transactions,
                               IdempotencyService idempotency) {
        this.authorizer=authorizer;
        this.members=members;
        this.transactions=transactions;
        this.idempotency=idempotency;
    }

    @PostMapping("/invitations")
    ResponseEntity<OfficeMemberInvitationView> invite(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody InviteRequest request,
            Authentication auth) {
        var admin=authorizer.require(auth,workspaceId,purpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.office.members.manage");
        members.requireAdmin(admin);
        String hash=RequestFingerprint.scoped(workspaceId,admin.actorId(),
                admin.purpose().name(),RequestFingerprint.sha256(INVITE,workspaceId,
                        request.actorId(),request.issuer(),request.subject(),request.role(),
                        request.expiresAt()));
        UUID invitation=DeterministicUuid.from(INVITE+".entity",workspaceId,key);
        UUID correlation=DeterministicUuid.from(INVITE,workspaceId,key);
        var result=idempotency.execute(workspaceId,key,INVITE,hash,201,
                OfficeMemberInvitationView.class,()->members.invite(admin,
                        request.actorId(),request.issuer(),request.subject(),request.role(),
                        request.expiresAt(),invitation,correlation));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    ResponseEntity<OfficeMembershipView> accept(
            @PathVariable UUID invitationId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth) {
        // Recipient is not a member YET. Intentionally do not require workspace
        // claim/membership from the onboarding token. Require validated JWT actor,
        // issuer, subject, purpose, scope and matching stored invitation instead.
        var recipient=members.recipientIdentity(auth,purpose);
        members.requireInvitationForRecipient(workspaceId,invitationId,recipient);
        String hash=RequestFingerprint.scoped(workspaceId,recipient.actorId(),
                "TRANSACTION_EXECUTION",RequestFingerprint.sha256(ACCEPT,workspaceId,
                        invitationId,recipient.issuer(),recipient.subject()));
        UUID correlation=DeterministicUuid.from(ACCEPT,workspaceId,key);
        var result=idempotency.execute(workspaceId,key,ACCEPT,hash,200,
                OfficeMembershipView.class,
                ()->members.accept(workspaceId,invitationId,recipient,correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/invitations/{invitationId}/cancel")
    ResponseEntity<OfficeMemberInvitationView> cancelInvitation(
            @PathVariable UUID invitationId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth) {
        var admin=authorizer.require(auth,workspaceId,purpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.office.members.manage");
        // An OAuth scope without institutional ADMIN is not enough even for a
        // previously successful idempotency replay.
        members.requireAdmin(admin);
        String hash=RequestFingerprint.scoped(workspaceId,admin.actorId(),
                admin.purpose().name(),RequestFingerprint.sha256(
                        CANCEL,workspaceId,invitationId));
        UUID correlation=DeterministicUuid.from(CANCEL,workspaceId,key);
        var result=idempotency.execute(workspaceId,key,CANCEL,hash,200,
                OfficeMemberInvitationView.class,
                ()->members.cancelInvitation(admin,invitationId,correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/{actorId}/revoke")
    ResponseEntity<OfficeMembershipView> revoke(
            @PathVariable UUID actorId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth) {
        var admin=authorizer.require(auth,workspaceId,purpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.office.members.manage");
        members.requireAdmin(admin);
        String hash=RequestFingerprint.scoped(workspaceId,admin.actorId(),
                admin.purpose().name(),RequestFingerprint.sha256(REVOKE,workspaceId,actorId));
        UUID correlation=DeterministicUuid.from(REVOKE,workspaceId,key);
        var result=idempotency.execute(workspaceId,key,REVOKE,hash,200,
                OfficeMembershipView.class,()->{
                    OfficeMembershipView revoked=members.revoke(admin,actorId,correlation);
                    // The same idempotency transaction also invalidates accepted
                    // and pending VIEWER grants. No stale deals on later rejoining.
                    transactions.revokeInstitutionalViewerGrants(
                            workspaceId,actorId,admin.actorId(),correlation);
                    return revoked;
                });
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    record InviteRequest(
            @NotNull UUID actorId,
            @NotBlank @Size(max=512) String issuer,
            @NotBlank @Size(max=256) String subject,
            @NotBlank String role,
            @NotNull @Future Instant expiresAt) {}
}
