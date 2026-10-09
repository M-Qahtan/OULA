package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.vitals.PropertyVitalSnapshot;
import com.oula.vitals.PropertyVitalsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
class PropertyVitalsController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PropertyVitalsService vitals;

    PropertyVitalsController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            PropertyVitalsService vitals
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.vitals = vitals;
    }

    @PostMapping("/v1/properties/{propertyId}/vitals/assess")
    ResponseEntity<PropertyVitalSnapshot> assess(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        String operation = "vitals.property.assess.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.property.vitals.assess"
        );
        String fingerprint = RequestFingerprint.sha256(operation, workspaceId, propertyId);
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                PropertyVitalSnapshot.class,
                () -> vitals.assess(access, propertyId, correlationId)
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/v1/properties/{propertyId}/vitals/latest")
    PropertyVitalSnapshot latest(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.property.vitals.read"
        );
        return vitals.latest(access, propertyId);
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
}
