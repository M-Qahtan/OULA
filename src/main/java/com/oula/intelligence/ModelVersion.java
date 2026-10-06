package com.oula.intelligence;
import java.time.Instant;
import java.util.UUID;
public record ModelVersion(UUID id, String modelId, String version, String modelType, String riskClass, String status, Instant releasedAt) {
  public ModelVersion {
    if (id == null || modelId == null || version == null || modelType == null || riskClass == null || status == null || releasedAt == null) throw new NullPointerException();
  }
}
