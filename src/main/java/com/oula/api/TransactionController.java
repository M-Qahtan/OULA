package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.transaction.TransactionApplicationService;
import com.oula.transaction.TransactionSnapshot;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
@RequestMapping("/v1/transactions")
class TransactionController {
    private static final String ADVANCE_OPERATION = "transaction.advance.v1";

    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final TransactionApplicationService transactions;

    TransactionController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            TransactionApplicationService transactions
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.transactions = transactions;
    }

    @GetMapping("/{transactionId}")
    TransactionResponse getTransaction(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.read"
        );

        TransactionSnapshot snapshot = transactions.get(transactionId);
        requireSameWorkspace(snapshot, workspaceId);
        return TransactionResponse.from(snapshot);
    }

    @PostMapping("/{transactionId}/transitions")
    ResponseEntity<TransactionResponse> advance(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionTransitionRequest request,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.advance"
        );

        TransactionSnapshot current = transactions.get(transactionId);
        requireSameWorkspace(current, workspaceId);

        String fingerprint = RequestFingerprint.sha256(
                ADVANCE_OPERATION,
                workspaceId,
                transactionId,
                request.expectedVersion(),
                request.target(),
                request.reason()
        );
        UUID correlationId = DeterministicUuid.from(
                ADVANCE_OPERATION,
                workspaceId,
                idempotencyKey
        );

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                ADVANCE_OPERATION,
                fingerprint,
                HttpStatus.OK.value(),
                TransactionResponse.class,
                () -> TransactionResponse.from(transactions.advance(
                        transactionId,
                        request.expectedVersion(),
                        request.target(),
                        access.actorId(),
                        request.reason(),
                        correlationId
                ))
        );

        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private void requireSameWorkspace(TransactionSnapshot snapshot, UUID workspaceId) {
        if (!snapshot.workspaceId().equals(workspaceId)) {
            throw new AccessDeniedException("transaction does not belong to the requested workspace");
        }
    }
}
