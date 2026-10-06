package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.matching.MatchingFacade;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/intents")
class MatchingController {
    private static final String OPERATION = "matching.run.v1";

    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final MatchingFacade matching;

    MatchingController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            MatchingFacade matching
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.matching = matching;
    }

    @PostMapping("/{intentId}/matches")
    ResponseEntity<MatchRunResponse> runMatching(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                "oula.matching.run"
        );

        String fingerprint = RequestFingerprint.sha256(
                OPERATION,
                workspaceId,
                intentId,
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );
        UUID correlationId = DeterministicUuid.from(
                OPERATION,
                workspaceId,
                idempotencyKey
        );

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                OPERATION,
                fingerprint,
                HttpStatus.CREATED.value(),
                MatchRunResponse.class,
                () -> MatchRunResponse.from(matching.run(intentId, workspaceId, correlationId))
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }
}
