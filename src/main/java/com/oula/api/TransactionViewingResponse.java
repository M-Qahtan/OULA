package com.oula.api;

import com.oula.transaction.ViewingRequestSnapshot;

import java.time.Instant;
import java.util.UUID;

public record TransactionViewingResponse(
        UUID id,
        UUID transactionId,
        Instant preferredAt,
        String note,
        String status,
        UUID requestedBy,
        Instant createdAt
) {
    static TransactionViewingResponse from(ViewingRequestSnapshot snapshot) {
        return new TransactionViewingResponse(
                snapshot.id(),
                snapshot.transactionId(),
                snapshot.preferredAt(),
                snapshot.note(),
                snapshot.status(),
                snapshot.requestedBy(),
                snapshot.createdAt()
        );
    }
}
