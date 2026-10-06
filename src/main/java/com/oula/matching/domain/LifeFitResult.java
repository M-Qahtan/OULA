package com.oula.matching.domain;

import java.util.Map;

public record LifeFitResult(
        double score,
        double confidence,
        Map<LifeFitDimension, Double> contributions
) {}
