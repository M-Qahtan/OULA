package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.property.PropertyCandidate;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LifeFitCalculator {
  public static final String ALGORITHM_VERSION = "lifefit-v2-decimal";
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final MathContext PRECISION = MathContext.DECIMAL128;

  public LifeFitResult score(Intent intent, PropertyCandidate property) {
    Objects.requireNonNull(intent, "intent");
    Objects.requireNonNull(property, "property");
    if (!intent.workspaceId().equals(property.workspaceId()))
      throw new IllegalArgumentException("candidate must belong to intent workspace");
    BigDecimal financial = financialFit(intent.budgetMax(), property.askingPrice());
    BigDecimal location = BigDecimal.valueOf(intent.preferredDistricts().isEmpty()
        || intent.preferredDistricts().contains(property.district()) ? 100 : 45);
    BigDecimal household = BigDecimal.valueOf(property.bedrooms() >= intent.minimumBedrooms() ? 100 : 0);
    BigDecimal mobility = clamp(BigDecimal.valueOf(100L - Math.max(0L, (long) property.commuteMinutes() - 15L) * 2L));
    BigDecimal coverage = property.totalFacts() == 0 ? BigDecimal.ZERO
        : BigDecimal.valueOf(property.verifiedFacts()).divide(BigDecimal.valueOf(property.totalFacts()), PRECISION);
    BigDecimal truth = coverage.multiply(HUNDRED);
    Map<String, Double> dimensions = new LinkedHashMap<>();
    dimensions.put("financial", round(financial));
    dimensions.put("location", round(location));
    dimensions.put("household", round(household));
    dimensions.put("mobility", round(mobility));
    dimensions.put("truth", round(truth));
    BigDecimal score = financial.multiply(new BigDecimal("0.30"))
        .add(location.multiply(new BigDecimal("0.20")))
        .add(household.multiply(new BigDecimal("0.20")))
        .add(mobility.multiply(new BigDecimal("0.15")))
        .add(truth.multiply(new BigDecimal("0.15")));
    // This remains an uncalibrated coverage heuristic, not a probability of correctness.
    BigDecimal confidence = new BigDecimal("0.55").add(coverage.multiply(new BigDecimal("0.45")));
    return new LifeFitResult(round(score), round(confidence), dimensions);
  }

  private BigDecimal financialFit(BigDecimal budget, BigDecimal price) {
    // Form monetary numerators exactly before division; do not quantize utilization to six decimals.
    if (price.compareTo(budget) <= 0) {
      BigDecimal excess = price.subtract(budget.multiply(new BigDecimal("0.85"))).max(BigDecimal.ZERO);
      return clamp(HUNDRED.subtract(excess.multiply(HUNDRED).divide(budget, PRECISION)));
    }
    return clamp(new BigDecimal("60").subtract(price.subtract(budget)
        .multiply(new BigDecimal("200")).divide(budget, PRECISION)));
  }
  private BigDecimal clamp(BigDecimal value) { return value.max(BigDecimal.ZERO).min(HUNDRED); }
  private double round(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).doubleValue(); }
}
