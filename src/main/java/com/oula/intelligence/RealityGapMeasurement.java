package com.oula.intelligence;

import java.math.BigDecimal;

public record RealityGapMeasurement(
    BigDecimal expectedValue,
    BigDecimal actualValue,
    BigDecimal absoluteGap,
    BigDecimal relativeGap,
    RealityGapClassification classification
) {}
