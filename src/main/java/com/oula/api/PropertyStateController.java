package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.property.PropertyStateSnapshot;
import com.oula.property.PropertyStateSnapshotService;
import com.oula.property.RecordPropertyStateSnapshotCommand;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/properties/{propertyId}/state-snapshots")
class PropertyStateController {
    private static final String PURPOSE = "PROPERTY_DECISION_SUPPORT";

    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PropertyStateSnapshotService snapshots;

    PropertyStateController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            PropertyStateSnapshotService snapshots
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.snapshots = snapshots;
    }

    @PostMapping
    ResponseEntity<PropertyStateSnapshot> record(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SnapshotRequest request,
            Authentication authentication
    ) {
        if ("CANONICAL_FACTS".equals(request.stateBasis()) || "MIXED".equals(request.stateBasis())) {
            throw new IllegalArgumentException(
                    "external snapshot API may not claim canonical or mixed property truth"
            );
        }

        String operation = "property.state_snapshot.record.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.property.snapshot.write"
        );
        List<UUID> evidenceRefs = sorted(request.evidenceRefs());
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                propertyId,
                request.effectiveAt(),
                request.stateBasis(),
                request.state(),
                request.sourceType(),
                request.sourceReference(),
                evidenceRefs
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                PropertyStateSnapshot.class,
                () -> snapshots.record(
                        access,
                        propertyId,
                        new RecordPropertyStateSnapshotCommand(
                                request.effectiveAt(),
                                request.stateBasis(),
                                request.state(),
                                request.sourceType(),
                                request.sourceReference(),
                                evidenceRefs
                        ),
                        correlationId
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/latest")
    PropertyStateSnapshot latest(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) Instant asOf,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.property.snapshot.read"
        );
        return snapshots.latestKnownAt(access, propertyId, asOf);
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

    record SnapshotRequest(
            Instant effectiveAt,
            @NotBlank @Size(max = 32) String stateBasis,
            @NotNull Map<String, Object> state,
            @NotBlank @Size(max = 80) String sourceType,
            @Size(max = 255) String sourceReference,
            List<UUID> evidenceRefs
    ) {
    }
}
