package com.oula.property;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
public record PropertyCandidate(UUID propertyId, UUID workspaceId, BigDecimal askingPrice, int bedrooms, String district, int commuteMinutes, int verifiedFacts, int totalFacts) {
  public PropertyCandidate {
    Objects.requireNonNull(propertyId); Objects.requireNonNull(workspaceId); Objects.requireNonNull(askingPrice); Objects.requireNonNull(district);
    if (askingPrice.signum() <= 0) throw new IllegalArgumentException("askingPrice must be positive");
    if (bedrooms < 0 || commuteMinutes < 0 || verifiedFacts < 0 || totalFacts < 0 || verifiedFacts > totalFacts) throw new IllegalArgumentException("invalid property candidate values");
  }
  public double truthCoverage() { return totalFacts == 0 ? 0.0 : (double) verifiedFacts / totalFacts; }
}
