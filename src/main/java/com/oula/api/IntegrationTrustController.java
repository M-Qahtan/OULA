package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.integration.*;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/integrations")
class IntegrationTrustController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final IntegrationTrustService integrations;

    IntegrationTrustController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            IntegrationTrustService integrations
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.integrations = integrations;
    }

    @PostMapping("/partners")
    ResponseEntity<IntegrationPartner> registerPartner(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PartnerRequest request,
            Authentication authentication
    ) {
        String operation = "integration.partner.register.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose,
                "oula.integration.partner.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.partnerCode(),
                request.partnerType(), request.displayName(),
                request.jurisdiction(), request.authMode(),
                request.credentialReference(), request.inboundEnabled(),
                request.outboundEnabled()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.CREATED.value(), IntegrationPartner.class,
                () -> integrations.registerPartner(
                        access,
                        new CreateIntegrationPartnerCommand(
                                request.partnerCode(), request.partnerType(),
                                request.displayName(), request.jurisdiction(),
                                request.authMode(), request.credentialReference(),
                                request.inboundEnabled(), request.outboundEnabled()
                        ),
                        correlation
                )
        );
        return created(result.replayed(), result.value());
    }

    @PostMapping("/partners/{partnerId}/verify")
    ResponseEntity<IntegrationPartner> verifyPartner(
            @PathVariable UUID partnerId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody VerifyPartnerRequest request,
            Authentication authentication
    ) {
        String operation = "integration.partner.verify.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose,
                "oula.integration.partner.verify"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, partnerId, request.evidenceId()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.OK.value(), IntegrationPartner.class,
                () -> integrations.verifyPartner(
                        access, partnerId, request.evidenceId(), correlation
                )
        );
        return ok(result.replayed(), result.value());
    }

    @PostMapping("/partners/{partnerId}/contracts")
    ResponseEntity<IntegrationContract> createContract(
            @PathVariable UUID partnerId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ContractRequest request,
            Authentication authentication
    ) {
        String operation = "integration.contract.create.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose,
                "oula.integration.contract.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, partnerId,
                request.contractKey(), request.version(), request.direction(),
                request.integrationPurpose(), request.operation(),
                request.resourceType(), request.allowedDataClasses(),
                request.effectiveFrom(), request.effectiveUntil()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.CREATED.value(), IntegrationContract.class,
                () -> integrations.createContract(
                        access, partnerId,
                        new CreateIntegrationContractCommand(
                                request.contractKey(), request.version(),
                                request.direction(), request.integrationPurpose(),
                                request.operation(), request.resourceType(),
                                request.allowedDataClasses(),
                                request.effectiveFrom(), request.effectiveUntil()
                        ),
                        correlation
                )
        );
        return created(result.replayed(), result.value());
    }

    @PostMapping("/partners/{partnerId}/outbound-requests")
    ResponseEntity<OutboundIntegrationRequest> prepareOutbound(
            @PathVariable UUID partnerId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OutboundRequest request,
            Authentication authentication
    ) {
        String operation = "integration.outbound.prepare.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose,
                "oula.integration.outbound.prepare"
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, partnerId,
                request.operation(), request.resourceType(),
                request.resourceId(), request.integrationPurpose(),
                request.dataClasses(), request.payloadHash(),
                request.payloadReference()
        );
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.CREATED.value(), OutboundIntegrationRequest.class,
                () -> integrations.prepareOutbound(
                        access, partnerId,
                        new PrepareOutboundIntegrationCommand(
                                request.operation(), request.resourceType(),
                                request.resourceId(), request.integrationPurpose(),
                                request.dataClasses(), request.payloadHash(),
                                request.payloadReference(), correlation
                        )
                )
        );
        return created(result.replayed(), result.value());
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String scope
    ) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.INTEGRATION_OPERATIONS, scope
        );
    }

    private <T> ResponseEntity<T> created(boolean replayed, T value) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    private <T> ResponseEntity<T> ok(boolean replayed, T value) {
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    record PartnerRequest(
            @NotBlank @Size(max = 80) String partnerCode,
            @NotBlank @Size(max = 40) String partnerType,
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Size(max = 80) String jurisdiction,
            @NotBlank @Size(max = 40) String authMode,
            @NotBlank @Size(max = 255) String credentialReference,
            boolean inboundEnabled,
            boolean outboundEnabled
    ) {}

    record VerifyPartnerRequest(@NotNull UUID evidenceId) {}

    record ContractRequest(
            @NotBlank @Size(max = 120) String contractKey,
            @NotBlank @Size(max = 40) String version,
            @NotBlank @Size(max = 16) String direction,
            @NotBlank @Size(max = 64) String integrationPurpose,
            @NotBlank @Size(max = 120) String operation,
            @NotBlank @Size(max = 80) String resourceType,
            @NotEmpty @Size(max = 50) Set<@NotBlank @Size(max = 80) String> allowedDataClasses,
            Instant effectiveFrom,
            Instant effectiveUntil
    ) {}

    record OutboundRequest(
            @NotBlank @Size(max = 120) String operation,
            @NotBlank @Size(max = 80) String resourceType,
            @NotNull UUID resourceId,
            @NotBlank @Size(max = 64) String integrationPurpose,
            @NotEmpty @Size(max = 50) Set<@NotBlank @Size(max = 80) String> dataClasses,
            @NotBlank @Size(max = 128) String payloadHash,
            @NotBlank @Size(max = 2000) String payloadReference
    ) {}
}
