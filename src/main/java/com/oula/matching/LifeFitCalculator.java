package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.property.PropertyCandidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LifeFitCalculator {
    public LifeFitResult score(Intent intent, PropertyCandidate property) {
        double financial = financialFit(intent.budgetMax(), property.askingPrice());
        double location = intent.preferredDistricts().isEmpty()
                || intent.preferredDistricts().contains(property.district())
                ? 100.0 : 45.0;
        double household = property.bedrooms() >= intent.minimumBedrooms() ? 100.0 : 0.0;
        double truth = property.truthCoverage() * 100.0;

        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("financial", round(financial));
        dimensions.put("location", round(location));
        dimensions.put("household", round(household));
        dimensions.put("truth", round(truth));

        double weighted = financial * 0.30
                + location * 0.20
                + household * 0.20
                + truth * 0.15;
        double knownWeight = 0.85;

        if (property.mobilityKnown()) {
            double mobility = clamp(
                    100.0 - Math.max(0, property.commuteMinutes() - 15) * 2.0
            );
            dimensions.put("mobility", round(mobility));
            weighted += mobility * 0.15;
            knownWeight += 0.15;
        }

        double score = weighted / knownWeight;
        double baseConfidence = 0.55 + property.truthCoverage() * 0.45;
        double confidence = baseConfidence * knownWeight;

        return new LifeFitResult(
                round(score),
                round(Math.min(1.0, confidence)),
                Map.copyOf(dimensions)
        );
    }

    private double financialFit(BigDecimal budget, BigDecimal price) {
        if (price.compareTo(budget) <= 0) {
            double utilization = price.divide(budget, 6, RoundingMode.HALF_UP).doubleValue();
            return clamp(100.0 - Math.max(0.0, utilization - 0.85) * 100.0);
        }
        double over = price.subtract(budget)
                .divide(budget, 6, RoundingMode.HALF_UP)
                .doubleValue();
        return clamp(60.0 - over * 200.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
