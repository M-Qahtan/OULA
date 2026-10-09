package com.oula.vitals;

import java.math.BigDecimal;

public record PropertyVitalsPolicy(
        String policyKey,
        String version,
        BigDecimal costUtilizationAmber,
        BigDecimal costUtilizationRed,
        BigDecimal providerRatingAmber,
        BigDecimal providerRatingRed,
        BigDecimal providerCostVarianceAmber,
        BigDecimal providerCostVarianceRed,
        BigDecimal truthCoverageAmber,
        BigDecimal truthCoverageRed,
        int freshnessAmberDays,
        int freshnessRedDays
) {}
