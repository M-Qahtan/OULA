package com.oula.api;

import com.oula.documents.EvidenceApplicationService;
import com.oula.documents.EvidenceArtifactView;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/evidence")
class EvidenceController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final EvidenceApplicationService evidence;

    EvidenceController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            EvidenceApplicationService evidence
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.evidence = evidence;
    }

    @PostMapping
    ResponseEntity<EvidenceArtifactView> register(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EvidenceRequest request,
            Authentication authentication
    ) {
        String operation = "documents.evidence.register.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.evidence.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.evidenceType(), request.source(),
                request.contentHash(), request.capturedAt()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), EvidenceArtifactView.class,
                () -> evidence.register(
                        access,
                        request.evidenceType(),
                        request.source(),
                        request.contentHash(),
                        request.capturedAt(),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/{evidenceId}/verify")
    ResponseEntity<EvidenceArtifactView> verify(
            @PathVariable UUID evidenceId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VerifyRequest request,
            Authentication authentication
    ) {
        String operation = "documents.evidence.verify.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.evidence.verify"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, evidenceId, request.reason()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), EvidenceArtifactView.class,
                () -> evidence.verify(access, evidenceId, request.reason(), correlationId)
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/{evidenceId}")
    EvidenceArtifactView get(
            @PathVariable UUID evidenceId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return evidence.get(
                authorize(authentication, workspaceId, requestedPurpose, "oula.evidence.read"),
                evidenceId
        );
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String scope
    ) {
        return authorizer.require(
                authentication,
                workspaceId,
                purpose,
                AccessPurpose.MARKETPLACE_SUPPLY,
                scope
        );
    }

    record EvidenceRequest(
            @NotBlank @Size(max = 64) String evidenceType,
            @NotBlank @Size(max = 160) String source,
            @NotBlank @Size(max = 128) String contentHash,
            Instant capturedAt
    ) {}

    record VerifyRequest(@NotBlank @Size(max = 1000) String reason) {}
}
