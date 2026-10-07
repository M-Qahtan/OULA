package com.oula.intelligence;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public final class RealityGapCalculator {
  private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

  public RealityGapMeasurement measure(
      BigDecimal expected,
      BigDecimal actual,
      RealityGapPolicy policy
  ) {
    Objects.requireNonNull(policy);

    if (expected == null || actual == null) {
      return new RealityGapMeasurement(
          expected, actual, null, null, RealityGapClassification.UNKNOWN);
    }

    BigDecimal absoluteGap = actual.subtract(expected, MATH_CONTEXT);

    if (expected.signum() == 0) {
      RealityGapClassification classification =
          actual.signum() == 0
              ? RealityGapClassification.WITHIN_EXPECTATION
              : RealityGapClassification.UNKNOWN;
      return new RealityGapMeasurement(expected, actual, absoluteGap, null, classification);
    }

    BigDecimal relativeGap =
        absoluteGap.divide(expected.abs(), MATH_CONTEXT);
    BigDecimal magnitude = relativeGap.abs();

    RealityGapClassification classification;
    if (magnitude.compareTo(policy.withinThreshold()) <= 0) {
      classification = RealityGapClassification.WITHIN_EXPECTATION;
    } else if (magnitude.compareTo(policy.minorThreshold()) <= 0) {
      classification = RealityGapClassification.MINOR_VARIANCE;
    } else if (magnitude.compareTo(policy.materialThreshold()) <= 0) {
      classification = RealityGapClassification.MATERIAL_VARIANCE;
    } else {
      classification = RealityGapClassification.SEVERE_VARIANCE;
    }

    return new RealityGapMeasurement(
        expected, actual, absoluteGap, relativeGap, classification);
  }
}
