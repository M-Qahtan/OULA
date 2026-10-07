package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.orchestration.TransactionOpeningOrchestrator;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
class TransactionOriginController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final TransactionOpeningOrchestrator orchestrator;

    TransactionOriginController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            TransactionOpeningOrchestrator orchestrator
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/from-decisions/{decisionId}")
    ResponseEntity<TransactionResponse> openFromDecision(
            @PathVariable UUID decisionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        String operation = "transaction.open.from_decision.v1";
        AccessContext access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.open"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, decisionId
        );
        UUID correlationId = DeterministicUuid.from(
                operation, workspaceId, idempotencyKey
        );
        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                TransactionResponse.class,
                () -> TransactionResponse.from(
                        orchestrator.openFromDecision(access, decisionId, correlationId)
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }
}
