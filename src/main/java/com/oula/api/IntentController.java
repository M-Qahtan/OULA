package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.intent.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/intents")
class IntentController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final IntentApplicationService intents;

    IntentController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            IntentApplicationService intents
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.intents = intents;
    }

    @PostMapping
    ResponseEntity<IntentView> create(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody IntentRequest request,
            Authentication authentication
    ) {
        String operation = "intent.create.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose, "oula.intent.write");
        UpsertIntentCommand command = command(request);
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.intentType(), request.budgetMax(),
                request.minimumBedrooms(), request.preferredDistricts()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), IntentView.class,
                () -> intents.createDraft(access, command, correlationId)
        );
        return created(result.value(), result.replayed());
    }

    @PutMapping("/{intentId}")
    ResponseEntity<IntentView> updateDraft(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody IntentUpdateRequest request,
            Authentication authentication
    ) {
        String operation = "intent.update.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose, "oula.intent.write");
        UpsertIntentCommand command = command(request.intent());
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, intentId, request.expectedVersion(),
                request.intent().intentType(), request.intent().budgetMax(),
                request.intent().minimumBedrooms(), request.intent().preferredDistricts()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), IntentView.class,
                () -> intents.updateDraft(
                        access, intentId, request.expectedVersion(), command, correlationId
                )
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/{intentId}/activate")
    ResponseEntity<IntentView> activate(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionRequest request,
            Authentication authentication
    ) {
        String operation = "intent.activate.v1";
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose, "oula.intent.activate");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, intentId, request.expectedVersion()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), IntentView.class,
                () -> intents.activate(access, intentId, request.expectedVersion(), correlationId)
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/{intentId}")
    IntentView get(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return intents.get(
                authorize(authentication, workspaceId, requestedPurpose, "oula.intent.read"),
                intentId
        );
    }

    private AccessContext authorize(Authentication authentication, UUID workspaceId, String purpose, String scope) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT, scope
        );
    }

    private UpsertIntentCommand command(IntentRequest request) {
        return new UpsertIntentCommand(
                request.intentType(),
                request.budgetMax(),
                request.minimumBedrooms(),
                request.preferredDistricts() == null ? Set.of() : Set.copyOf(request.preferredDistricts())
        );
    }

    private ResponseEntity<IntentView> created(IntentView value, boolean replayed) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    record IntentRequest(
            @NotBlank String intentType,
            @NotNull @DecimalMin("0.01") BigDecimal budgetMax,
            @NotNull @Min(0) Integer minimumBedrooms,
            Set<String> preferredDistricts
    ) {}

    record IntentUpdateRequest(@Min(0) long expectedVersion, @Valid @NotNull IntentRequest intent) {}
    record VersionRequest(@Min(0) long expectedVersion) {}
}
