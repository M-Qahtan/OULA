package com.oula.intelligence;

import java.math.BigDecimal;

public record RealityGapMeasurement(
        BigDecimal expectedValue,
        BigDecimal actualValue,
        BigDecimal signedError,
        BigDecimal absoluteError,
        BigDecimal relativeError,
        RealityGapClassification classification
) {
}
