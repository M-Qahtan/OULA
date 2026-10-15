package com.oula.transaction;

import com.oula.platform.UuidV7;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
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
        String normalizedCurrency = Objects.requireNonNull(currency, "currency").trim().toUpperCase();
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }

        TransactionSnapshot current = get(transactionId);
        requireWorkspace(current, workspaceId);
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
