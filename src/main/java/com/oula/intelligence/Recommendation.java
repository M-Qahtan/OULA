package com.oula.intelligence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record Recommendation(UUID id, UUID workspaceId, UUID intentId, UUID recommendedPropertyId, List<UUID> alternatives, String modelId, String modelVersion, double confidence, Instant generatedAt, UUID correlationId) {
  public Recommendation {
    if (id == null || workspaceId == null || intentId == null || recommendedPropertyId == null || modelId == null || modelVersion == null || generatedAt == null || correlationId == null) throw new NullPointerException();
    alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be 0..1");
  }
}
