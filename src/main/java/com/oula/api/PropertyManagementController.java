package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.operations.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
class PropertyManagementController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PropertyManagementService management;

    PropertyManagementController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            PropertyManagementService management
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.management = management;
    }

    @PostMapping("/v1/properties/{propertyId}/management/enroll")
    ResponseEntity<ManagementEnrollment> enroll(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        String operation = "operations.management.enroll.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose,
                "oula.property.management.write");
        String fingerprint = RequestFingerprint.sha256(operation, workspaceId, propertyId);
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), ManagementEnrollment.class,
                () -> management.enroll(access, propertyId, correlationId)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/v1/properties/{propertyId}/management")
    ManagementOverview overview(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose,
                "oula.property.management.read");
        return management.overview(access, propertyId);
    }

    @PostMapping("/v1/properties/{propertyId}/obligations")
    ResponseEntity<Obligation> createObligation(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ObligationRequest request,
            Authentication authentication
    ) {
        String operation = "operations.obligation.create.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose,
                "oula.property.management.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, propertyId, request.obligationType(),
                request.title(), request.dueAt(), request.priority(),
                request.sourceType(), request.sourceReference()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), Obligation.class,
                () -> management.createObligation(
                        access,
                        propertyId,
                        new CreateObligationCommand(
                                request.obligationType(),
                                request.title(),
                                request.dueAt(),
                                request.priority(),
                                request.sourceType(),
                                request.sourceReference()
                        ),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/v1/properties/{propertyId}/guardian/assess")
    ResponseEntity<GuardianAssessment> assess(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        String operation = "operations.guardian.assess.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose,
                "oula.property.guardian.run");
        String fingerprint = RequestFingerprint.sha256(operation, workspaceId, propertyId);
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), GuardianAssessment.class,
                () -> management.assess(access, propertyId, correlationId)
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/v1/property-actions/{actionId}/complete")
    ResponseEntity<ActionItem> completeAction(
            @PathVariable UUID actionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompleteActionRequest request,
            Authentication authentication
    ) {
        String operation = "operations.action.complete.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose,
                "oula.property.action.complete");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, actionId, request.resolutionNote()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), ActionItem.class,
                () -> management.completeAction(
                        access, actionId, request.resolutionNote(), correlationId
                )
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String requestedPurpose,
            String scope
    ) {
        return authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.PROPERTY_MANAGEMENT,
                scope
        );
    }

    record ObligationRequest(
            @NotBlank @Size(max = 80) String obligationType,
            @NotBlank @Size(max = 255) String title,
            @NotNull Instant dueAt,
            @NotBlank @Size(max = 24) String priority,
            @NotBlank @Size(max = 80) String sourceType,
            @Size(max = 255) String sourceReference
    ) {}

    record CompleteActionRequest(
            @NotBlank @Size(max = 1000) String resolutionNote
    ) {}
}
