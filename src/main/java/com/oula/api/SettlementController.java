package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.settlement.SettlementReference;
import com.oula.settlement.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
class SettlementController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final SettlementService settlements;

    SettlementController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            SettlementService settlements
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.settlements = settlements;
    }

    @PostMapping("/v1/work-orders/{workOrderId}/settlements")
    ResponseEntity<SettlementReference> record(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody SettlementRequest request,
            Authentication authentication
    ) {
        String operation = "settlement.reference.record.v1";
        AccessContext access = authorize(
                authentication, workspaceId, purpose, "oula.settlement.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, workOrderId, request.processorCode(),
                request.externalReference(), request.amount(), request.currency(),
                request.status(), request.evidenceId()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                SettlementReference.class,
                () -> settlements.record(
                        access, workOrderId, request.processorCode(),
                        request.externalReference(), request.amount(),
                        request.currency(), request.status(), request.evidenceId(),
                        correlation
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/v1/work-orders/{workOrderId}/settlements")
    List<SettlementReference> list(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        return settlements.list(
                authorize(authentication, workspaceId, purpose, "oula.settlement.read"),
                workOrderId
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

    record SettlementRequest(
            @NotBlank @Size(max = 80) String processorCode,
            @NotBlank @Size(max = 255) String externalReference,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Pattern(regexp = "PENDING|SETTLED|FAILED|REVERSED") String status,
            UUID evidenceId
    ) {}
}
