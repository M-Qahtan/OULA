package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.intent.IntentApplicationService;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/intents")
class IntentController {
    private static final String CREATE_OPERATION = "intent.create.v1";
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
    ResponseEntity<IntentResponse> create(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody IntentCreateRequest request,
            Authentication authentication
    ) {
        authorizer.require(authentication, workspaceId, requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT, "oula.intent.write");

        String fingerprint = RequestFingerprint.sha256(
                CREATE_OPERATION, workspaceId, request.intentType(), request.budgetMax(),
                request.minimumBedrooms(), request.preferredDistricts().stream().sorted().toList()
        );
        UUID intentId = DeterministicUuid.from(CREATE_OPERATION, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, CREATE_OPERATION, fingerprint, HttpStatus.CREATED.value(),
                IntentResponse.class, () -> IntentResponse.from(intents.create(
                        intentId, workspaceId, request.intentType(), request.budgetMax(),
                        request.minimumBedrooms(), request.preferredDistricts()))
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/{intentId}")
    ResponseEntity<IntentResponse> get(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        authorizer.require(authentication, workspaceId, requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT, "oula.intent.read");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IntentResponse.from(intents.get(intentId, workspaceId)));
    }
}
