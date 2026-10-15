package com.oula.transaction;

import java.time.Instant;
import java.util.UUID;

public record ViewingRequestSnapshot(
        UUID id,
        UUID transactionId,
        UUID workspaceId,
        Instant preferredAt,
        String note,
        String status,
        UUID requestedBy,
        Instant createdAt
) {
}
