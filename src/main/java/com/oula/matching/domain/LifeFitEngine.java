package com.oula.matching.domain;

import java.util.EnumMap;
import java.util.Map;

/** Deterministic, explainable baseline. Inputs are normalized to [0,100]. */
public final class LifeFitEngine {
    private static final Map<LifeFitDimension, Double> DEFAULT_WEIGHTS = Map.of(
            LifeFitDimension.FINANCIAL, 0.25,
            LifeFitDimension.LOCATION, 0.20,
            LifeFitDimension.HOUSEHOLD, 0.15,
            LifeFitDimension.MOBILITY, 0.10,
            LifeFitDimension.FUTURE, 0.10,
            LifeFitDimension.PROPERTY, 0.10,
            LifeFitDimension.RISK, 0.10
    );

    public LifeFitResult score(Map<LifeFitDimension, Double> dimensions, double dataCompleteness) {
        if (dimensions == null || dimensions.isEmpty()) {
            throw new IllegalArgumentException("At least one LifeFit dimension is required");
        }
        if (dataCompleteness < 0 || dataCompleteness > 1) {
            throw new IllegalArgumentException("dataCompleteness must be between 0 and 1");
        }

        double presentWeight = dimensions.keySet().stream().mapToDouble(DEFAULT_WEIGHTS::get).sum();
        var contributions = new EnumMap<LifeFitDimension, Double>(LifeFitDimension.class);
        double total = 0;

        for (var entry : dimensions.entrySet()) {
            double value = requireScore(entry.getValue());
            double normalizedWeight = DEFAULT_WEIGHTS.get(entry.getKey()) / presentWeight;
            double contribution = value * normalizedWeight;
            contributions.put(entry.getKey(), contribution);
            total += contribution;
        }

        double dimensionCoverage = presentWeight;
        double confidence = clamp01(0.65 * dataCompleteness + 0.35 * dimensionCoverage);
        return new LifeFitResult(round2(total), round4(confidence), Map.copyOf(contributions));
    }

    private static double requireScore(double score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException("Dimension score must be [0,100]");
        return score;
    }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
    private static double round2(double value) { return Math.round(value * 100.0) / 100.0; }
    private static double round4(double value) { return Math.round(value * 10000.0) / 10000.0; }
}
