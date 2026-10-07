package com.oula.api;

import com.oula.documents.EvidenceApplicationService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.property.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/properties")
class PropertyController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PropertyApplicationService properties;
    private final EvidenceApplicationService evidence;

    PropertyController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            PropertyApplicationService properties,
            EvidenceApplicationService evidence
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.properties = properties;
        this.evidence = evidence;
    }

    @PostMapping
    ResponseEntity<PropertyAssetView> create(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PropertyRequest request,
            Authentication authentication
    ) {
        String operation = "property.asset.create.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.property.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.assetType(), request.district(), request.bedrooms()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), PropertyAssetView.class,
                () -> properties.createAsset(
                        access,
                        request.assetType(),
                        request.district(),
                        request.bedrooms(),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/{propertyId}")
    PropertyAssetView get(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return properties.get(
                authorize(authentication, workspaceId, requestedPurpose, "oula.property.read"),
                propertyId
        );
    }

    @PostMapping("/{propertyId}/facts")
    ResponseEntity<PropertyFactView> recordFact(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FactRequest request,
            Authentication authentication
    ) {
        String operation = "property.fact.record.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.property.fact.write"
        );
        if (request.truthStatus() == TruthStatus.OBSERVED) {
            evidence.requireVerified(access, request.evidenceReference());
        }
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, propertyId, request.factKey(), request.value(),
                request.truthStatus(), request.sourceType(), request.confidence(),
                request.visibility(), request.evidenceReference()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), PropertyFactView.class,
                () -> properties.recordFact(
                        access,
                        propertyId,
                        new RecordPropertyFactCommand(
                                request.factKey(),
                                request.value(),
                                request.truthStatus(),
                                request.sourceType(),
                                request.confidence(),
                                request.visibility(),
                                request.evidenceReference()
                        ),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/{propertyId}/facts/{factId}/verify")
    ResponseEntity<PropertyFactView> verifyFact(
            @PathVariable UUID propertyId,
            @PathVariable UUID factId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VerifyFactRequest request,
            Authentication authentication
    ) {
        String operation = "property.fact.verify.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.property.fact.verify"
        );
        evidence.requireVerified(access, request.evidenceId());
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, propertyId, factId,
                request.expectedVersion(), request.evidenceId()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), PropertyFactView.class,
                () -> properties.verifyFact(
                        access,
                        propertyId,
                        factId,
                        request.expectedVersion(),
                        request.evidenceId(),
                        correlationId
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
            String purpose,
            String scope
    ) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.MARKETPLACE_SUPPLY, scope
        );
    }

    record PropertyRequest(
            @NotBlank String assetType,
            @NotBlank @Size(max = 160) String district,
            @NotNull @Min(0) Integer bedrooms
    ) {}

    record FactRequest(
            @NotBlank @Size(max = 160) String factKey,
            @NotNull Map<String, Object> value,
            @NotNull TruthStatus truthStatus,
            @NotBlank @Size(max = 80) String sourceType,
            @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
            @NotBlank String visibility,
            UUID evidenceReference
    ) {}

    record VerifyFactRequest(@Min(0) long expectedVersion, @NotNull UUID evidenceId) {}
}
