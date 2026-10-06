package com.oula.market;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ListingSnapshot(UUID listingId, UUID workspaceId, UUID propertyId, String transactionType, BigDecimal askingPrice, String currency, String status, Instant observedAt) {
  public ListingSnapshot {
    if (listingId == null || workspaceId == null || propertyId == null || transactionType == null || askingPrice == null || currency == null || status == null || observedAt == null) throw new NullPointerException();
    if (askingPrice.signum() <= 0) throw new IllegalArgumentException("askingPrice must be positive");
  }
}
