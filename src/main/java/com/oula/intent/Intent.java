package com.oula.intent;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
public record Intent(UUID id, UUID workspaceId, BigDecimal budgetMax, int minimumBedrooms, Set<String> preferredDistricts, IntentStatus status) {
  public Intent {
    Objects.requireNonNull(id); Objects.requireNonNull(workspaceId); Objects.requireNonNull(budgetMax); Objects.requireNonNull(preferredDistricts); Objects.requireNonNull(status);
    if (budgetMax.signum() <= 0) throw new IllegalArgumentException("budgetMax must be positive");
    if (minimumBedrooms < 0) throw new IllegalArgumentException("minimumBedrooms must be non-negative");
    preferredDistricts = Set.copyOf(preferredDistricts);
  }
  public boolean isActive() { return status == IntentStatus.ACTIVE; }
}
