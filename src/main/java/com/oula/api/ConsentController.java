package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.ConsentService;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/consents")
class ConsentController {
    private static final String DECISION_OPERATION = "iam.consent.decision.v1";

    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final ConsentService consents;

    ConsentController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            ConsentService consents
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.consents = consents;
    }

    @GetMapping
    List<ConsentResponse> current(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessPurpose purpose = parsePurpose(requestedPurpose);
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                purpose,
                "oula.consent.read"
        );
        return consents.current(access).stream().map(ConsentResponse::from).toList();
    }

    @PostMapping("/decisions")
    ResponseEntity<ConsentResponse> decide(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConsentDecisionRequest request,
            Authentication authentication
    ) {
        AccessPurpose purpose = parsePurpose(requestedPurpose);
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                purpose,
                "oula.consent.write"
        );

        String fingerprint = RequestFingerprint.sha256(
                DECISION_OPERATION,
                workspaceId,
                access.actorId(),
                purpose,
                request.consentCode(),
                request.decision(),
                request.reason()
        );
        UUID eventId = DeterministicUuid.from(
                DECISION_OPERATION + ".event",
                workspaceId,
                idempotencyKey
        );
        UUID correlationId = DeterministicUuid.from(
                DECISION_OPERATION,
                workspaceId,
                idempotencyKey
        );

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                DECISION_OPERATION,
                fingerprint,
                HttpStatus.CREATED.value(),
                ConsentResponse.class,
                () -> ConsentResponse.from(consents.record(
                        access,
                        request.consentCode(),
                        request.decision(),
                        request.reason(),
                        eventId,
                        correlationId
                ))
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private AccessPurpose parsePurpose(String requestedPurpose) {
        try {
            return AccessPurpose.valueOf(requestedPurpose);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("requested purpose is not recognized");
        }
    }
}
