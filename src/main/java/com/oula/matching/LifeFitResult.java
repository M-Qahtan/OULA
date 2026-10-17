package com.oula.matching;
import java.util.Map;
public record LifeFitResult(double score, double confidence, Map<String, Double> dimensions) {
  public LifeFitResult {
    if (!Double.isFinite(score) || score < 0 || score > 100) throw new IllegalArgumentException("score must be finite and between 0 and 100");
    if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
    dimensions = Map.copyOf(dimensions);
    if (dimensions.values().stream().anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 100))
      throw new IllegalArgumentException("dimensions must be finite and between 0 and 100");
  }
}
