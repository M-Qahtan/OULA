package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.leasing.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
class LeasingController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final LeasingService leasing;

    LeasingController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            LeasingService leasing
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.leasing = leasing;
    }

    @PostMapping("/v1/properties/{propertyId}/leases")
    ResponseEntity<Lease> createDraft(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateLeaseRequest request,
            Authentication authentication
    ) {
        String operation = "leasing.lease.create_draft.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose, "oula.leasing.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, propertyId,
                request.landlordPartyId(), request.tenantPartyId(),
                request.leaseType(), request.startsAt(), request.endsAt(),
                request.rentAmount(), request.currency(),
                request.paymentFrequency(), request.securityDeposit(),
                request.externalContractReference(), request.sourceType()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.CREATED.value(), Lease.class,
                () -> leasing.createDraft(
                        access, propertyId,
                        new CreateLeaseCommand(
                                request.landlordPartyId(), request.tenantPartyId(),
                                request.leaseType(), request.startsAt(), request.endsAt(),
                                request.rentAmount(), request.currency(),
                                request.paymentFrequency(), request.securityDeposit(),
                                request.externalContractReference(), request.sourceType()
                        ),
                        correlation
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/v1/leases/{leaseId}/activate")
    ResponseEntity<Lease> activate(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("X-OULA-Jurisdiction") String jurisdiction,
            @RequestHeader(value = "X-OULA-Approval-ID", required = false) UUID approvalId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActivateLeaseRequest request,
            Authentication authentication
    ) {
        String operation = "leasing.lease.activate.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose, "oula.leasing.activate"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, leaseId, request.contractEvidenceId(),
                jurisdiction, approvalId
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.OK.value(), Lease.class,
                () -> leasing.activate(
                        access, leaseId, request.contractEvidenceId(),
                        jurisdiction, approvalId, correlation
                )
        );
        return ok(result.replayed(), result.value());
    }

    @PostMapping("/v1/leases/{leaseId}/terminate")
    ResponseEntity<Lease> terminate(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("X-OULA-Jurisdiction") String jurisdiction,
            @RequestHeader(value = "X-OULA-Approval-ID", required = false) UUID approvalId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TerminateLeaseRequest request,
            Authentication authentication
    ) {
        String operation = "leasing.lease.terminate.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose, "oula.leasing.terminate"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, leaseId, jurisdiction,
                approvalId, request.reason()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.OK.value(), Lease.class,
                () -> leasing.terminate(
                        access, leaseId, jurisdiction, approvalId,
                        request.reason(), correlation
                )
        );
        return ok(result.replayed(), result.value());
    }

    @GetMapping("/v1/properties/{propertyId}/leases")
    List<Lease> list(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        return leasing.list(
                authorize(authentication, workspaceId, purpose, "oula.leasing.read"),
                propertyId
        );
    }

    @GetMapping("/v1/properties/{propertyId}/occupancy/current")
    OccupancyPeriod currentOccupancy(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        return leasing.currentOccupancy(
                authorize(authentication, workspaceId, purpose, "oula.leasing.read"),
                propertyId
        );
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String scope
    ) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, scope
        );
    }

    private <T> ResponseEntity<T> ok(boolean replayed, T value) {
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    record CreateLeaseRequest(
            @NotNull UUID landlordPartyId,
            @NotNull UUID tenantPartyId,
            @NotBlank @Size(max = 32) String leaseType,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal rentAmount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 24) String paymentFrequency,
            @DecimalMin("0.0") BigDecimal securityDeposit,
            @Size(max = 255) String externalContractReference,
            @NotBlank @Size(max = 80) String sourceType
    ) {}

    record ActivateLeaseRequest(@NotNull UUID contractEvidenceId) {}

    record TerminateLeaseRequest(
            @NotBlank @Size(max = 4000) String reason
    ) {}
}
