package com.oula.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RealityGapCalculatorTest {

  private final RealityGapCalculator calculator = new RealityGapCalculator();
  private final RealityGapPolicy policy = new RealityGapPolicy(
      "default-relative-gap",
      "v1",
      new BigDecimal("0.05"),
      new BigDecimal("0.15"),
      new BigDecimal("0.30"));

  @Test
  void classifiesBoundaryAndSevereVarianceDeterministically() {
    assertThat(measure("100", "105").classification())
        .isEqualTo(RealityGapClassification.WITHIN_EXPECTATION);
    assertThat(measure("100", "115").classification())
        .isEqualTo(RealityGapClassification.MINOR_VARIANCE);
    assertThat(measure("100", "130").classification())
        .isEqualTo(RealityGapClassification.MATERIAL_VARIANCE);
    assertThat(measure("100", "131").classification())
        .isEqualTo(RealityGapClassification.SEVERE_VARIANCE);
  }

  @Test
  void preservesDirectionWhileClassifyingByMagnitude() {
    RealityGapMeasurement result = measure("100", "80");

    assertThat(result.absoluteGap()).isEqualByComparingTo("-20");
    assertThat(result.relativeGap()).isEqualByComparingTo("-0.2");
    assertThat(result.classification()).isEqualTo(RealityGapClassification.MATERIAL_VARIANCE);
  }

  @Test
  void makesZeroDenominatorSemanticsExplicit() {
    RealityGapMeasurement unknown = measure("0", "10");
    RealityGapMeasurement bothZero = measure("0", "0");

    assertThat(unknown.relativeGap()).isNull();
    assertThat(unknown.classification()).isEqualTo(RealityGapClassification.UNKNOWN);
    assertThat(bothZero.relativeGap()).isNull();
    assertThat(bothZero.classification()).isEqualTo(RealityGapClassification.WITHIN_EXPECTATION);
  }

  @Test
  void missingMeasurementIsUnknown() {
    RealityGapMeasurement result = calculator.measure(null, new BigDecimal("10"), policy);

    assertThat(result.classification()).isEqualTo(RealityGapClassification.UNKNOWN);
    assertThat(result.absoluteGap()).isNull();
    assertThat(result.relativeGap()).isNull();
  }

  private RealityGapMeasurement measure(String expected, String actual) {
    return calculator.measure(new BigDecimal(expected), new BigDecimal(actual), policy);
  }
}
