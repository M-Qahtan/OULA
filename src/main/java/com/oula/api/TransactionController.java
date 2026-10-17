package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.transaction.DealRoomSnapshot;
import com.oula.transaction.DealGrantSnapshot;
import com.oula.transaction.TransactionApplicationService;
import com.oula.transaction.TransactionSnapshot;
import com.oula.transaction.TransactionStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import java.time.Instant;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
class TransactionController {
    private static final String CREATE_OPERATION = "transaction.create.v1";
    private static final String VIEWING_OPERATION = "transaction.viewing.request.v1";
    private static final String OFFER_OPERATION = "transaction.offer.submit.v1";
    private static final String ADVANCE_OPERATION = "transaction.advance.v1";
    private static final String GRANT_READ_OPERATION = "transaction.read_access.grant.v1";
    private static final String REVOKE_READ_OPERATION = "transaction.read_access.revoke.v1";
    private static final String ACCEPT_READ_OPERATION = "transaction.read_access.accept.v1";

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

    @PostMapping
    ResponseEntity<TransactionResponse> create(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionCreateRequest request,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.create"
        );

        String fingerprint = RequestFingerprint.sha256(
                CREATE_OPERATION,
                workspaceId,
                request.intentId(),
                request.propertyId()
        );
        UUID transactionId = DeterministicUuid.from(
                CREATE_OPERATION + ".entity",
                workspaceId,
                idempotencyKey
        );
        UUID correlationId = DeterministicUuid.from(
                CREATE_OPERATION,
                workspaceId,
                idempotencyKey
        );

        fingerprint = RequestFingerprint.scoped(access.workspaceId(), access.actorId(), access.purpose().name(), fingerprint);
        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                CREATE_OPERATION,
                fingerprint,
                HttpStatus.CREATED.value(),
                TransactionResponse.class,
                () -> TransactionResponse.from(transactions.create(
                        transactionId,
                        workspaceId,
                        request.intentId(),
                        request.propertyId(),
                        access.actorId(),
                        correlationId
                ).transaction())
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/{transactionId}")
    TransactionResponse getTransaction(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.read"
        );
        transactions.requireCanRead(transactionId, workspaceId, access.actorId());

        TransactionSnapshot snapshot = transactions.get(transactionId);
        requireSameWorkspace(snapshot, workspaceId);
        return TransactionResponse.from(snapshot);
    }

    @GetMapping("/{transactionId}/deal-room")
    DealRoomResponse dealRoom(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.read"
        );
        transactions.requireCanRead(transactionId, workspaceId, access.actorId());
        return toDealRoomResponse(
                transactions.dealRoom(transactionId, workspaceId),
                authentication,
                transactions.canCommand(transactionId, workspaceId, access.actorId())
        );
    }

    @PostMapping("/{transactionId}/viewings")
    ResponseEntity<TransactionViewingCommandResponse> requestViewing(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionViewingRequest request,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.viewing.request"
        );

        String fingerprint = RequestFingerprint.sha256(
                VIEWING_OPERATION,
                workspaceId,
                transactionId,
                request.expectedVersion(),
                request.preferredAt(),
                request.note(),
                request.humanConfirmed()
        );
        UUID correlationId = DeterministicUuid.from(
                VIEWING_OPERATION,
                workspaceId,
                idempotencyKey
        );
        UUID viewingId = DeterministicUuid.from(
                VIEWING_OPERATION + ".entity",
                workspaceId,
                idempotencyKey
        );
        transactions.requireCanCommand(transactionId, workspaceId, access.actorId());

        fingerprint = RequestFingerprint.scoped(access.workspaceId(), access.actorId(), access.purpose().name(), fingerprint);
        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                VIEWING_OPERATION,
                fingerprint,
                HttpStatus.CREATED.value(),
                TransactionViewingCommandResponse.class,
                () -> {
                    DealRoomSnapshot room = transactions.requestViewing(
                            transactionId,
                            workspaceId,
                            request.expectedVersion(),
                            request.preferredAt(),
                            request.note(),
                            request.humanConfirmed(),
                            access.actorId(),
                            viewingId,
                            correlationId
                    );
                    var viewing = room.viewings().stream()
                            .filter(item -> item.id().equals(viewingId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("viewing request was not persisted"));
                    return new TransactionViewingCommandResponse(
                            TransactionResponse.from(room.transaction()),
                            TransactionViewingResponse.from(viewing)
                    );
                }
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/{transactionId}/offers")
    ResponseEntity<TransactionOfferCommandResponse> submitOffer(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionOfferRequest request,
            Authentication authentication
    ) {
        var access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,
                "oula.transaction.offer.submit"
        );

        String normalizedCurrency = request.currency().trim().toUpperCase(Locale.ROOT);
        String fingerprint = RequestFingerprint.sha256(
                OFFER_OPERATION,
                workspaceId,
                transactionId,
                request.expectedVersion(),
                request.amount(),
                normalizedCurrency,
                request.termsSummary(),
                request.humanConfirmed()
        );
        UUID correlationId = DeterministicUuid.from(
                OFFER_OPERATION,
                workspaceId,
                idempotencyKey
        );
        UUID offerId = DeterministicUuid.from(
                OFFER_OPERATION + ".entity",
                workspaceId,
                idempotencyKey
        );
        transactions.requireCanCommand(transactionId, workspaceId, access.actorId());

        fingerprint = RequestFingerprint.scoped(access.workspaceId(), access.actorId(), access.purpose().name(), fingerprint);
        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                OFFER_OPERATION,
                fingerprint,
                HttpStatus.CREATED.value(),
                TransactionOfferCommandResponse.class,
                () -> {
                    DealRoomSnapshot room = transactions.submitOffer(
                            transactionId,
                            workspaceId,
                            request.expectedVersion(),
                            request.amount(),
                            normalizedCurrency,
                            request.termsSummary(),
                            request.humanConfirmed(),
                            access.actorId(),
                            offerId,
                            correlationId
                    );
                    var offer = room.offers().stream()
                            .filter(item -> item.id().equals(offerId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("offer was not persisted"));
                    return new TransactionOfferCommandResponse(
                            TransactionResponse.from(room.transaction()),
                            TransactionOfferResponse.from(offer)
                    );
                }
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
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

        if (request.target() == TransactionStage.VIEWING) {
            throw new IllegalArgumentException("VIEWING requires the dedicated human-confirmed viewing endpoint");
        }
        if (request.target() == TransactionStage.OFFERING) {
            throw new IllegalArgumentException("OFFERING requires the dedicated human-confirmed offer endpoint");
        }

        TransactionSnapshot current = transactions.get(transactionId);
        requireSameWorkspace(current, workspaceId);
        transactions.requireCanCommand(transactionId, workspaceId, access.actorId());

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

        fingerprint = RequestFingerprint.scoped(access.workspaceId(), access.actorId(), access.purpose().name(), fingerprint);
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

    @PostMapping("/{transactionId}/read-grants")
    ResponseEntity<DealGrantSnapshot> grantRead(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReadGrantRequest request,
            Authentication authentication
    ) {
        var access = authorizer.require(authentication,workspaceId,requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.transaction.grant.manage");
        transactions.requireCanCommand(transactionId,workspaceId,access.actorId());
        String fingerprint = RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(GRANT_READ_OPERATION,workspaceId,transactionId,
                        request.actorId(),request.expiresAt()));
        UUID correlation=DeterministicUuid.from(GRANT_READ_OPERATION,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,GRANT_READ_OPERATION,
                fingerprint,HttpStatus.CREATED.value(),DealGrantSnapshot.class,
                ()->transactions.grantRead(transactionId,workspaceId,access.actorId(),
                        request.actorId(),request.expiresAt(),correlation));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(result.value());
    }

    @PostMapping("/{transactionId}/read-grants/accept")
    ResponseEntity<DealGrantSnapshot> acceptRead(
            @PathVariable UUID transactionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        var access = authorizer.require(authentication,workspaceId,requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.transaction.read");
        // Also before the idempotency lookup: a revoked or expired invitation
        // must never receive a cached successful accept response.
        Instant invitationIssuedAt=transactions.requireActiveReadInvitation(
                transactionId,workspaceId,access.actorId());
        String fingerprint=RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(ACCEPT_READ_OPERATION,workspaceId,
                        transactionId,invitationIssuedAt));
        UUID correlation=DeterministicUuid.from(ACCEPT_READ_OPERATION,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,ACCEPT_READ_OPERATION,
                fingerprint,HttpStatus.OK.value(),DealGrantSnapshot.class,
                ()->transactions.acceptRead(transactionId,workspaceId,access.actorId(),correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @PostMapping("/{transactionId}/read-grants/{actorId}/revoke")
    ResponseEntity<DealGrantSnapshot> revokeRead(
            @PathVariable UUID transactionId,
            @PathVariable UUID actorId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        var access=authorizer.require(authentication,workspaceId,requestedPurpose,
                AccessPurpose.TRANSACTION_EXECUTION,"oula.transaction.grant.manage");
        transactions.requireCanCommand(transactionId,workspaceId,access.actorId());
        String fingerprint=RequestFingerprint.scoped(workspaceId,access.actorId(),access.purpose().name(),
                RequestFingerprint.sha256(REVOKE_READ_OPERATION,workspaceId,transactionId,actorId));
        UUID correlation=DeterministicUuid.from(REVOKE_READ_OPERATION,workspaceId,idempotencyKey);
        var result=idempotency.execute(workspaceId,idempotencyKey,REVOKE_READ_OPERATION,
                fingerprint,HttpStatus.OK.value(),DealGrantSnapshot.class,
                ()->transactions.revokeRead(transactionId,workspaceId,access.actorId(),actorId,correlation));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(result.value());
    }

    record ReadGrantRequest(@NotNull UUID actorId, @NotNull @Future Instant expiresAt) {}

    private DealRoomResponse toDealRoomResponse(
            DealRoomSnapshot room,
            Authentication authentication,
            boolean canCommand
    ) {
        List<String> available = new ArrayList<>();
        if (room.transaction().stage() == TransactionStage.DRAFT && canCommand
                && hasScope(authentication, "oula.transaction.advance")) {
            available.add("QUALIFY");
        }
        if (room.transaction().stage() == TransactionStage.QUALIFIED && canCommand
                && hasScope(authentication, "oula.transaction.viewing.request")) {
            available.add("REQUEST_VIEWING");
        }
        if (room.transaction().stage() == TransactionStage.VIEWING && canCommand
                && !room.viewings().isEmpty()
                && hasScope(authentication, "oula.transaction.offer.submit")) {
            available.add("SUBMIT_OFFER");
        }

        return new DealRoomResponse(
                TransactionResponse.from(room.transaction()),
                room.viewings().stream().map(TransactionViewingResponse::from).toList(),
                room.offers().stream().map(TransactionOfferResponse::from).toList(),
                available,
                true
        );
    }

    private boolean hasScope(Authentication authentication, String scope) {
        String authority = "SCOPE_" + scope;
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private void requireSameWorkspace(TransactionSnapshot snapshot, UUID workspaceId) {
        if (!snapshot.workspaceId().equals(workspaceId)) {
            throw new AccessDeniedException("transaction does not belong to the requested workspace");
        }
    }
}
