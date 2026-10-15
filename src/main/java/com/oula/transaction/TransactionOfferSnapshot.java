package com.oula.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionOfferSnapshot(
        UUID id,
        UUID transactionId,
        UUID workspaceId,
        BigDecimal amount,
        String currency,
        String termsSummary,
        String status,
        UUID submittedBy,
        Instant createdAt
) {
}
