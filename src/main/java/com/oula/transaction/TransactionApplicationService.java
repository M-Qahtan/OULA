package com.oula.transaction;

import com.oula.platform.UuidV7;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
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

        outbox.append(
                "transaction.stage.changed.v1",
                "Transaction",
                transactionId,
                current.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "transactionId", transactionId,
                        "fromStage", current.stage().name(),
                        "toStage", next.name(),
                        "actorId", actorId,
                        "version", expectedVersion + 1,
                        "historyId", historyId
                )
        );

        return new TransactionSnapshot(
                current.id(),
                current.workspaceId(),
                next,
                expectedVersion + 1
        );
    }
}
