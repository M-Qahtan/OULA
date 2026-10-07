package com.oula.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingView(
        UUID listingId,
        UUID workspaceId,
        UUID propertyId,
        String transactionType,
        String status,
        BigDecimal askingPrice,
        String currency,
        Instant publishedAt,
        Instant withdrawnAt,
        long version
) {
}
