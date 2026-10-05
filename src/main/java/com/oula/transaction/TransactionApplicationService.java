package com.oula.transaction;

import com.oula.platform.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionApplicationService {
    private final TransactionRepository repository;
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    public TransactionApplicationService(TransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TransactionSnapshot advance(
            UUID transactionId,
            long expectedVersion,
            TransactionStage target,
            UUID actorId,
            String reason
    ) {
        TransactionSnapshot current = repository.find(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("transaction not found: " + transactionId));

        if (current.version() != expectedVersion) {
            throw new OptimisticConcurrencyException(transactionId, expectedVersion);
        }

        TransactionStage next = stateMachine.transition(current.stage(), target);
        repository.advance(transactionId, expectedVersion, next);
        repository.recordTransition(
                UuidV7.next(),
                transactionId,
                current.stage(),
                next,
                actorId,
                reason
        );

        return new TransactionSnapshot(
                current.id(),
                current.workspaceId(),
                next,
                expectedVersion + 1
        );
    }
}
