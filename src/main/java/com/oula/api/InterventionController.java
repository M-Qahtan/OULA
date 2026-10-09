package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.interventions.*;
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

import java.util.UUID;

@RestController
class InterventionController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final InterventionService interventions;

    InterventionController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            InterventionService interventions
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.interventions = interventions;
    }

    @PostMapping("/v1/properties/{propertyId}/intervention-reviews")
    ResponseEntity<InterventionReview> review(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication
    ) {
        String operation = "interventions.review.record.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.property.interventions.review.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, propertyId, request.sourceSnapshotId(),
                request.dimension(), request.actionCode(), request.decision(),
                request.rationale());
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var saved = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                InterventionReview.class,
                () -> interventions.review(
                        access, propertyId,
                        new RecordInterventionReviewCommand(
                                request.sourceSnapshotId(), request.dimension(),
                                request.actionCode(), request.decision(), request.rationale()),
                        correlation)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(saved.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(saved.value());
    }

    @PostMapping("/v1/intervention-reviews/{reviewId}/outcome")
    ResponseEntity<InterventionOutcome> observe(
            @PathVariable UUID reviewId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OutcomeRequest request,
            Authentication authentication
    ) {
        String operation = "interventions.outcome.observe.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.property.interventions.outcome.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, reviewId, request.afterSnapshotId(),
                request.completedWorkOrderId(), request.observationNote());
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var saved = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                InterventionOutcome.class,
                () -> interventions.observe(
                        access, reviewId,
                        new RecordInterventionOutcomeCommand(
                                request.afterSnapshotId(),
                                request.completedWorkOrderId(),
                                request.observationNote()), correlation)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(saved.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(saved.value());
    }

    @GetMapping("/v1/properties/{propertyId}/intervention-reviews")
    ResponseEntity<InterventionHistory> history(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.property.interventions.read");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(interventions.history(access, propertyId));
    }

    private AccessContext authorize(
            Authentication authentication, UUID workspaceId,
            String purpose, String scope
    ) {
        return authorizer.require(authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, scope);
    }

    record ReviewRequest(
            @NotNull UUID sourceSnapshotId,
            @NotBlank @Size(max = 40) String dimension,
            @NotBlank @Size(max = 100) String actionCode,
            @NotBlank @Pattern(regexp = "ACKNOWLEDGED|DECLINED|DEFERRED")
            String decision,
            @NotBlank @Size(max = 2000) String rationale
    ) {}

    record OutcomeRequest(
            @NotNull UUID afterSnapshotId,
            UUID completedWorkOrderId,
            @NotBlank @Size(max = 2000) String observationNote
    ) {}
}
