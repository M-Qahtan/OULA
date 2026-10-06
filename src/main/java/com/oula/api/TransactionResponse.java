package com.oula.api;

import com.oula.transaction.TransactionSnapshot;
import com.oula.transaction.TransactionStage;

import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID workspaceId,
        TransactionStage stage,
        long version
) {
    static TransactionResponse from(TransactionSnapshot snapshot) {
        return new TransactionResponse(
                snapshot.id(),
                snapshot.workspaceId(),
                snapshot.stage(),
                snapshot.version()
        );
    }
}
