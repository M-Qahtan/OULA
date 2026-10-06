package com.oula.matching;
import java.util.Map;
public record LifeFitResult(double score, double confidence, Map<String, Double> dimensions) {
  public LifeFitResult {
    if (score < 0 || score > 100) throw new IllegalArgumentException("score must be between 0 and 100");
    if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
    dimensions = Map.copyOf(dimensions);
  }
}
