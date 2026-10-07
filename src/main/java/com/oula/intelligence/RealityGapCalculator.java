package com.oula.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class RealityGapCalculator {
    public RealityGapMeasurement measure(
            BigDecimal expectedValue,
            BigDecimal actualValue,
            RealityGapPolicy policy
    ) {
        Objects.requireNonNull(expectedValue, "expectedValue");
        Objects.requireNonNull(actualValue, "actualValue");
        Objects.requireNonNull(policy, "policy");

        BigDecimal expected = expectedValue.setScale(6, RoundingMode.HALF_UP);
        BigDecimal actual = actualValue.setScale(6, RoundingMode.HALF_UP);
        BigDecimal signedError = expected.subtract(actual).setScale(6, RoundingMode.HALF_UP);
        BigDecimal absoluteError = signedError.abs().setScale(6, RoundingMode.HALF_UP);

        if (expected.signum() == 0) {
            RealityGapClassification classification = actual.signum() == 0
                    ? RealityGapClassification.WITHIN_EXPECTATION
                    : RealityGapClassification.UNKNOWN;
            return new RealityGapMeasurement(
                    expected, actual, signedError, absoluteError, null, classification
            );
        }

        BigDecimal relativeError = absoluteError.divide(
                expected.abs(), 8, RoundingMode.HALF_UP
        );
        RealityGapClassification classification;
        if (relativeError.compareTo(policy.withinThreshold()) <= 0) {
            classification = RealityGapClassification.WITHIN_EXPECTATION;
        } else if (relativeError.compareTo(policy.minorThreshold()) <= 0) {
            classification = RealityGapClassification.MINOR_VARIANCE;
        } else if (relativeError.compareTo(policy.materialThreshold()) <= 0) {
            classification = RealityGapClassification.MATERIAL_VARIANCE;
        } else {
            classification = RealityGapClassification.SEVERE_VARIANCE;
        }

        return new RealityGapMeasurement(
                expected, actual, signedError, absoluteError, relativeError, classification
        );
    }
}
