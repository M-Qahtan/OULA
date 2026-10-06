package com.oula.people;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public record LifeGraphSnapshot(UUID personId, UUID householdId, int householdSize, BigDecimal maxHousingBudget, Set<UUID> mobilityAnchors, Instant capturedAt) {
  public LifeGraphSnapshot {
    if (personId == null || maxHousingBudget == null || capturedAt == null) throw new NullPointerException();
    mobilityAnchors = mobilityAnchors == null ? Set.of() : Set.copyOf(mobilityAnchors);
    if (householdSize < 1 || maxHousingBudget.signum() <= 0) throw new IllegalArgumentException("invalid LifeGraph snapshot");
  }
}
