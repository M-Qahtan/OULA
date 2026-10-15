package com.oula.api;

import com.oula.transaction.TransactionOfferSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionOfferResponse(
        UUID id,
        UUID transactionId,
        BigDecimal amount,
        String currency,
        String termsSummary,
        String status,
        UUID submittedBy,
        Instant createdAt
) {
    static TransactionOfferResponse from(TransactionOfferSnapshot snapshot) {
        return new TransactionOfferResponse(
                snapshot.id(),
                snapshot.transactionId(),
                snapshot.amount(),
                snapshot.currency(),
                snapshot.termsSummary(),
                snapshot.status(),
                snapshot.submittedBy(),
                snapshot.createdAt()
        );
    }
}
