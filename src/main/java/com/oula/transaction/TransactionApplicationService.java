package com.oula.transaction;

import com.oula.platform.UuidV7;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionApplicationService {
    private final TransactionRepository repository;
    private final OutboxWriter outbox;
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    public TransactionApplicationService(
            TransactionRepository repository,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public TransactionSnapshot get(UUID transactionId) {
        return repository.find(transactionId)
                .orElseThrow(() -> new NoSuchElementException("transaction not found: " + transactionId));
    }

    @Transactional(readOnly = true)
    public DealRoomSnapshot dealRoom(UUID transactionId, UUID workspaceId) {
        TransactionSnapshot transaction = get(transactionId);
        requireWorkspace(transaction, workspaceId);
        return new DealRoomSnapshot(
                transaction,
                repository.viewings(transactionId, workspaceId),
                repository.offers(transactionId, workspaceId)
        );
    }

    @Transactional
    public DealRoomSnapshot create(
            UUID transactionId,
            UUID workspaceId,
            UUID intentId,
            UUID propertyId,
            UUID actorId,
            UUID correlationId
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(correlationId, "correlationId");

        if (!repository.intentBelongsToWorkspace(intentId, workspaceId)
                || !repository.propertyBelongsToWorkspace(propertyId, workspaceId)) {
            throw new SecurityException("transaction references are not available in the requested workspace");
        }
        if (repository.hasOpenTransaction(workspaceId, intentId, propertyId)) {
            throw new IllegalStateException("an open transaction already exists for this intent and property");
        }

        repository.create(transactionId, workspaceId, intentId, propertyId);
        repository.insertOriginatorGrant(transactionId, workspaceId, actorId);
        UUID historyId = UuidV7.next();
        repository.recordCreation(
                historyId,
                transactionId,
                actorId,
                "transaction created from workspace-scoped intent and property"
        );

        outbox.append(
                "transaction.created.v1",
                "Transaction",
                transactionId,
                workspaceId,
                correlationId,
                correlationId,
                Map.of(
                        "transactionId", transactionId,
                        "workspaceId", workspaceId,
                        "intentId", intentId,
                        "propertyId", propertyId,
                        "actorId", actorId,
                        "stage", TransactionStage.DRAFT.name(),
                        "version", 0,
                        "historyId", historyId
                )
        );

        return dealRoom(transactionId, workspaceId);
    }

    @Transactional
    public DealRoomSnapshot requestViewing(
            UUID transactionId,
            UUID workspaceId,
            long expectedVersion,
            Instant preferredAt,
            String note,
            boolean humanConfirmed,
            UUID actorId,
            UUID viewingId,
            UUID correlationId
    ) {
        if (!humanConfirmed) {
            throw new IllegalArgumentException("explicit human confirmation is required for a viewing request");
        }

        TransactionSnapshot current = get(transactionId);
        requireWorkspace(current, workspaceId);
        requireCanCommand(transactionId, workspaceId, actorId);
        requireVersion(current, expectedVersion);

        TransactionStage next = stateMachine.transition(current.stage(), TransactionStage.VIEWING);
        repository.createViewing(
                viewingId,
                transactionId,
                workspaceId,
                preferredAt,
                note,
                actorId
        );
        UUID historyId = UuidV7.next();
        repository.advance(transactionId, expectedVersion, next);
        repository.recordTransition(
                historyId,
                transactionId,
                current.stage(),
                next,
                actorId,
                "human-confirmed viewing request"
        );

        appendStageChanged(current, next, actorId, expectedVersion + 1, historyId, correlationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewingId", viewingId);
        payload.put("transactionId", transactionId);
        payload.put("actorId", actorId);
        payload.put("humanConfirmed", true);
        if (preferredAt != null) payload.put("preferredAt", preferredAt.toString());
        if (note != null && !note.isBlank()) payload.put("note", note);
        outbox.append(
                "transaction.viewing.requested.v1",
                "Transaction",
                transactionId,
                workspaceId,
                correlationId,
                correlationId,
                payload
        );

        return dealRoom(transactionId, workspaceId);
    }

    @Transactional
    public DealRoomSnapshot submitOffer(
            UUID transactionId,
            UUID workspaceId,
            long expectedVersion,
            BigDecimal amount,
            String currency,
            String termsSummary,
            boolean humanConfirmed,
            UUID actorId,
            UUID offerId,
            UUID correlationId
    ) {
        if (!humanConfirmed) {
            throw new IllegalArgumentException("explicit human confirmation is required for an offer");
        }
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("offer amount must be positive");
        }
        String normalizedCurrency = Objects.requireNonNull(currency, "currency").trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }

        TransactionSnapshot current = get(transactionId);
        requireWorkspace(current, workspaceId);
        requireCanCommand(transactionId, workspaceId, actorId);
        requireVersion(current, expectedVersion);

        if (!repository.hasViewing(transactionId, workspaceId)) {
            throw new IllegalStateException("a human-confirmed viewing request is required before submitting an offer");
        }

        TransactionStage next = stateMachine.transition(current.stage(), TransactionStage.OFFERING);
        repository.createOffer(
                offerId,
                transactionId,
                workspaceId,
                amount,
                normalizedCurrency,
                termsSummary,
                actorId
        );
        UUID historyId = UuidV7.next();
        repository.advance(transactionId, expectedVersion, next);
        repository.recordTransition(
                historyId,
                transactionId,
                current.stage(),
                next,
                actorId,
                "human-confirmed offer submitted"
        );

        appendStageChanged(current, next, actorId, expectedVersion + 1, historyId, correlationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerId", offerId);
        payload.put("transactionId", transactionId);
        payload.put("actorId", actorId);
        payload.put("amount", amount);
        payload.put("currency", normalizedCurrency);
        payload.put("humanConfirmed", true);
        if (termsSummary != null && !termsSummary.isBlank()) {
            payload.put("termsSummary", termsSummary);
        }
        outbox.append(
                "transaction.offer.submitted.v1",
                "Transaction",
                transactionId,
                workspaceId,
                correlationId,
                correlationId,
                payload
        );

        return dealRoom(transactionId, workspaceId);
    }

    @Transactional
    public TransactionSnapshot advance(
            UUID transactionId,
            long expectedVersion,
            TransactionStage target,
            UUID actorId,
            String reason
    ) {
        return advance(
                transactionId,
                expectedVersion,
                target,
                actorId,
                reason,
                UuidV7.next()
        );
    }

    @Transactional
    public TransactionSnapshot advance(
            UUID transactionId,
            long expectedVersion,
            TransactionStage target,
            UUID actorId,
            String reason,
            UUID correlationId
    ) {
        TransactionSnapshot current = get(transactionId);
        requireCanCommand(transactionId, current.workspaceId(), actorId);

        if (current.version() != expectedVersion) {
            throw new OptimisticConcurrencyException(transactionId, expectedVersion);
        }

        TransactionStage next = stateMachine.transition(current.stage(), target);
        UUID historyId = UuidV7.next();

        repository.advance(transactionId, expectedVersion, next);
        repository.recordTransition(
                historyId,
                transactionId,
                current.stage(),
                next,
                actorId,
                reason
        );

        appendStageChanged(current, next, actorId, expectedVersion + 1, historyId, correlationId);

        return new TransactionSnapshot(
                current.id(),
                current.workspaceId(),
                next,
                expectedVersion + 1
        );
    }

    /** Scope + workspace are necessary but not sufficient to read an individual deal. */
    @Transactional(readOnly = true)
    public void requireCanRead(UUID transactionId, UUID workspaceId, UUID actorId) {
        if (!repository.hasGrant(transactionId, workspaceId, actorId, null)) {
            throw new AccessDeniedException("actor is not granted this transaction");
        }
    }

    @Transactional(readOnly = true)
    public void requireCanCommand(UUID transactionId, UUID workspaceId, UUID actorId) {
        if (!repository.hasGrant(transactionId, workspaceId, actorId, "ORIGINATOR")) {
            throw new AccessDeniedException("only the original deal owner can execute commands");
        }
    }

    @Transactional(readOnly = true)
    public boolean canCommand(UUID transactionId, UUID workspaceId, UUID actorId) {
        return repository.hasGrant(transactionId, workspaceId, actorId, "ORIGINATOR");
    }

    /** An invitation is not yet permission to see a deal. */
    @Transactional(readOnly = true)
    public long requireActiveReadInvitation(UUID transactionId, UUID workspaceId, UUID actorId) {
        return repository.activeViewerInvitationVersion(transactionId,workspaceId,actorId)
                .orElseThrow(()->new AccessDeniedException("an active unexpired invitation is required"));
    }

    /** Only the JWT-verified invited actor can activate its own read-only invitation. */
    @Transactional
    public DealGrantSnapshot acceptRead(UUID transactionId, UUID workspaceId,
                                        UUID actorId, UUID correlationId) {
        repository.lockDeal(transactionId, workspaceId);
        Instant expiresAt = repository.acceptViewer(transactionId,workspaceId,actorId)
                .orElseThrow(()->new IllegalStateException(
                        "invitation is not pending, is expired or has been revoked"));
        outbox.append("transaction.access.read_accepted.v1", "Transaction",transactionId,
                workspaceId,correlationId,correlationId,Map.of(
                        "transactionId",transactionId,"actorId",actorId,
                        "expiresAt",expiresAt.toString()));
        return new DealGrantSnapshot(transactionId,actorId,"VIEWER",true,true,expiresAt);
    }

    @Transactional
    public DealGrantSnapshot grantRead(UUID transactionId, UUID workspaceId,
                                      UUID grantorId, UUID targetActorId,
                                      Instant expiresAt, UUID correlationId) {
        Objects.requireNonNull(targetActorId, "targetActorId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(Instant.now()) || expiresAt.isAfter(Instant.now().plusSeconds(90L*86400L))) {
            throw new IllegalArgumentException("read delegation expiry must be within the next 90 days");
        }
        if (grantorId.equals(targetActorId)) {
            throw new IllegalArgumentException("originator cannot delegate to self");
        }
        repository.lockDeal(transactionId, workspaceId);
        requireCanCommand(transactionId, workspaceId, grantorId);
        repository.grantViewer(transactionId, workspaceId, targetActorId, grantorId, expiresAt);
        outbox.append("transaction.access.read_invited.v1", "Transaction", transactionId,
                workspaceId, correlationId, correlationId, Map.of(
                        "transactionId", transactionId, "actorId", targetActorId,
                        "grantedBy", grantorId, "expiresAt", expiresAt.toString()));
        return new DealGrantSnapshot(transactionId, targetActorId, "VIEWER", true, false, expiresAt);
    }

    @Transactional
    public DealGrantSnapshot revokeRead(UUID transactionId, UUID workspaceId,
                                        UUID grantorId, UUID targetActorId, UUID correlationId) {
        repository.lockDeal(transactionId, workspaceId);
        requireCanCommand(transactionId, workspaceId, grantorId);
        if (repository.revokeViewer(transactionId, workspaceId, targetActorId) != 1) {
            throw new java.util.NoSuchElementException("active viewer grant not found");
        }
        outbox.append("transaction.access.read_revoked.v1", "Transaction", transactionId,
                workspaceId, correlationId, correlationId, Map.of(
                        "transactionId", transactionId, "actorId", targetActorId,
                        "revokedBy", grantorId));
        return new DealGrantSnapshot(transactionId, targetActorId, "VIEWER", false, false, null);
    }

    /**
     * Office offboarding removes outstanding deal VIEWER grants atomically
     * with membership revocation when called from API's idempotent transaction.
     * Originator privileges must be separately reconciled before any trusted
     * reprovisioning; a revoked membership cannot access them.
     */
    @Transactional
    public int revokeInstitutionalViewerGrants(UUID workspaceId, UUID targetActorId,
                                               UUID administratorActorId, UUID correlationId) {
        List<UUID> revoked=repository.revokeAllInstitutionalViewerGrants(
                workspaceId,targetActorId);
        for (UUID transactionId : revoked) {
            outbox.append("transaction.access.read_revoked.v1","Transaction",
                    transactionId,workspaceId,correlationId,correlationId,Map.of(
                            "transactionId",transactionId,
                            "actorId",targetActorId,
                            "revokedBy",administratorActorId,
                            "reason","INSTITUTION_MEMBERSHIP_REVOKED"));
        }
        return revoked.size();
    }

    private void appendStageChanged(
            TransactionSnapshot current,
            TransactionStage next,
            UUID actorId,
            long version,
            UUID historyId,
            UUID correlationId
    ) {
        outbox.append(
                "transaction.stage.changed.v1",
                "Transaction",
                current.id(),
                current.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "transactionId", current.id(),
                        "fromStage", current.stage().name(),
                        "toStage", next.name(),
                        "actorId", actorId,
                        "version", version,
                        "historyId", historyId
                )
        );
    }

    private void requireWorkspace(TransactionSnapshot transaction, UUID workspaceId) {
        if (!transaction.workspaceId().equals(workspaceId)) {
            throw new SecurityException("transaction does not belong to the requested workspace");
        }
    }

    private void requireVersion(TransactionSnapshot transaction, long expectedVersion) {
        if (transaction.version() != expectedVersion) {
            throw new OptimisticConcurrencyException(transaction.id(), expectedVersion);
        }
    }
}
