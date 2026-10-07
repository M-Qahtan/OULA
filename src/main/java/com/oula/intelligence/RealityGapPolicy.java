package com.oula.intelligence;

import java.math.BigDecimal;
import java.util.Objects;

public record RealityGapPolicy(
    String key,
    String version,
    BigDecimal withinThreshold,
    BigDecimal minorThreshold,
    BigDecimal materialThreshold
) {
  public RealityGapPolicy {
    Objects.requireNonNull(key);
    Objects.requireNonNull(version);
    Objects.requireNonNull(withinThreshold);
    Objects.requireNonNull(minorThreshold);
    Objects.requireNonNull(materialThreshold);
    if (withinThreshold.signum() < 0
        || minorThreshold.compareTo(withinThreshold) < 0
        || materialThreshold.compareTo(minorThreshold) < 0) {
      throw new IllegalArgumentException("Reality gap thresholds must be ordered and non-negative");
    }
  }
}
