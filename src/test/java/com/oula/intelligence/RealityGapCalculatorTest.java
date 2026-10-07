package com.oula.intelligence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RealityGapCalculatorTest {
    private final RealityGapCalculator calculator = new RealityGapCalculator();
    private final RealityGapPolicy policy = new RealityGapPolicy(
            "commuteMinutes",
            "v1",
            new BigDecimal("0.05"),
            new BigDecimal("0.15"),
            new BigDecimal("0.30")
    );

    @Test
    void classifiesMaterialVarianceWithExplicitScientificSignConvention() {
        RealityGapMeasurement result = calculator.measure(
                new BigDecimal("22"),
                new BigDecimal("27"),
                policy
        );

        assertThat(result.signedError()).isEqualByComparingTo("-5.000000");
        assertThat(result.absoluteError()).isEqualByComparingTo("5.000000");
        assertThat(result.relativeError()).isEqualByComparingTo("0.22727273");
        assertThat(result.classification()).isEqualTo(
                RealityGapClassification.MATERIAL_VARIANCE
        );
    }

    @Test
    void refusesToInventRelativeMeaningWhenExpectedValueIsZero() {
        RealityGapMeasurement result = calculator.measure(
                BigDecimal.ZERO,
                BigDecimal.ONE,
                policy
        );

        assertThat(result.relativeError()).isNull();
        assertThat(result.classification()).isEqualTo(RealityGapClassification.UNKNOWN);
    }

    @Test
    void zeroExpectedAndZeroActualIsWithinExpectation() {
        RealityGapMeasurement result = calculator.measure(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                policy
        );

        assertThat(result.classification())
                .isEqualTo(RealityGapClassification.WITHIN_EXPECTATION);
    }
}
