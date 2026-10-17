package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.OfficeInvitationView;
import com.oula.iam.OfficeMemberView;
import com.oula.iam.OfficeMembershipService;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.iam.InstitutionalMembershipRegistry;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Office administration is not self-service enrollment. Only a trusted,
 * already-provisioned ADMIN may invite or revoke BROKER/STAFF members.
 */
@RestController
@RequestMapping("/v1/offices/members")
class OfficeMembershipController {
    private static final String INVITE="iam.office.members.invite.v1";
    private static final String ACCEPT="iam.office.members.accept.v1";
    private static final String REVOKE="iam.office.members.revoke.v1";

    private final WorkspacePurposeAuthorizer authorizer;
    private final OfficeMembershipService offices;
    private final InstitutionalMembershipRegistry memberships;
    private final IdempotencyService idempotency;

    OfficeMembershipController(WorkspacePurposeAuthorizer authorizer,
                                OfficeMembershipService offices,
                                InstitutionalMembershipRegistry memberships,
                                IdempotencyService idempotency) {
        this.authorizer=authorizer;
        this.offices=offices;
        this.memberships=memberships;
        this.idempotency=idempotency;
    }

    @GetMapping
    ResponseEntity<List<OfficeMemberView>> members(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        var access=authorizer.require(authentication,workspaceId,purpose,
                AccessPurpose.INTEGRATION_OPERATIONS,"oula.office.members.read");
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(offices.members(workspaceId,access.actorId(),access.subject(),issuer(authentication)));
    }

    @PostMapping("/invitations")
    ResponseEntity<OfficeInvitationView> invite(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InviteRequest request,
            Authentication authentication) {
        var access=authorizer.require(authentication,workspaceId,purpose,
                AccessPurpose.INTEGRATION_OPERATIONS,"oula.office.members.manage");
        memberships.requireActiveOfficeAdmin(workspaceId,access.actorId(),access.subject(),issuer(authentication));
        String fingerprint=RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(INVITE,workspaceId,request.actorId(),
                        request.subject(),request.role(),request.expiresAt()));
        UUID correlation=DeterministicUuid.from(INVITE,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,INVITE,fingerprint,
                HttpStatus.CREATED.value(),OfficeInvitationView.class,
                ()->offices.invite(workspaceId,access.actorId(),access.subject(),
                        issuer(authentication),request.actorId(),request.subject(),
                        request.role(),request.expiresAt(),correlation));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/invitations/accept")
    ResponseEntity<OfficeInvitationView> accept(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        // The invited actor is NOT a member yet: purpose/scope/actor/workspace
        // claims must be signed, and only this endpoint bypasses the membership
        // roster check, not the invitation identity/expiry/state check.
        var access=authorizer.requirePendingInstitutionalInvitation(
                authentication,workspaceId,purpose,AccessPurpose.INTEGRATION_OPERATIONS,
                "oula.office.members.accept");
        long version=offices.requireCurrentInvitation(workspaceId,access.actorId(),
                access.subject(),issuer(authentication));
        String fingerprint=RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(ACCEPT,workspaceId,version));
        UUID correlation=DeterministicUuid.from(ACCEPT,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,ACCEPT,fingerprint,
                HttpStatus.OK.value(),OfficeInvitationView.class,
                ()->offices.accept(workspaceId,access.actorId(),access.subject(),
                        issuer(authentication),correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/{actorId}/revoke")
    ResponseEntity<OfficeInvitationView> revoke(
            @PathVariable UUID actorId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        var access=authorizer.require(authentication,workspaceId,purpose,
                AccessPurpose.INTEGRATION_OPERATIONS,"oula.office.members.manage");
        memberships.requireActiveOfficeAdmin(workspaceId,access.actorId(),access.subject(),issuer(authentication));
        String fingerprint=RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(REVOKE,workspaceId,actorId));
        UUID correlation=DeterministicUuid.from(REVOKE,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,REVOKE,fingerprint,
                HttpStatus.OK.value(),OfficeInvitationView.class,
                ()->offices.revoke(workspaceId,access.actorId(),access.subject(),
                        issuer(authentication),actorId,correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    private URL issuer(Authentication authentication) {
        return ((JwtAuthenticationToken)authentication).getToken().getIssuer();
    }

    record InviteRequest(@NotNull UUID actorId,@NotBlank String subject,
                         @NotBlank String role,@NotNull @Future Instant expiresAt) {}
}
