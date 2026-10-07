package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.intelligence.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/intelligence")
class RealityScienceController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final RealityGapReviewService reviews;
    private final RealityMemoryService memory;

    RealityScienceController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            RealityGapReviewService reviews,
            RealityMemoryService memory
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.reviews = reviews;
        this.memory = memory;
    }

    @PostMapping("/reality-gaps/{gapId}/reviews")
    ResponseEntity<RealityGapView> reviewGap(
            @PathVariable UUID gapId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.reality_gap.review.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.review"
        );
        List<UUID> evidenceIds = sorted(request.evidenceIds());
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                gapId,
                request.causeCategory(),
                request.causeConfidence(),
                request.rationale(),
                request.reviewStatus(),
                request.calibrationStatus(),
                evidenceIds
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                RealityGapView.class,
                () -> reviews.review(
                        access,
                        gapId,
                        new ReviewRealityGapCommand(
                                request.causeCategory(),
                                request.causeConfidence(),
                                request.rationale(),
                                request.reviewStatus(),
                                request.calibrationStatus(),
                                evidenceIds
                        ),
                        correlationId
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/outcomes/{outcomeId}/reality-case")
    RealityCaseView realityCase(
            @PathVariable UUID outcomeId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return memory.realityCase(
                authorize(authentication, workspaceId, requestedPurpose, "oula.intelligence.read"),
                outcomeId
        );
    }

    @GetMapping("/models/{modelVersionId}/calibration")
    List<CalibrationProjectionView> calibration(
            @PathVariable UUID modelVersionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return memory.calibration(
                authorize(authentication, workspaceId, requestedPurpose, "oula.intelligence.read"),
                modelVersionId
        );
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
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                scope
        );
    }

    private List<UUID> sorted(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> copy = new ArrayList<>(ids);
        copy.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(copy);
    }

    record ReviewRequest(
            @NotNull ErrorCauseCategory causeCategory,
            @DecimalMin("0.0") @DecimalMax("1.0") Double causeConfidence,
            @NotBlank String rationale,
            @NotNull RealityGapReviewStatus reviewStatus,
            @NotNull CalibrationStatus calibrationStatus,
            List<UUID> evidenceIds
    ) {
    }
}
