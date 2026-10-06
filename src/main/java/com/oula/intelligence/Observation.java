package com.oula.intelligence;
import java.time.Instant;
import java.util.UUID;
public record Observation(UUID id, UUID workspaceId, String subjectType, UUID subjectId, String phenomenon, String valueJson, String sourceType, double qualityScore, Instant observedAt) {
  public Observation {
    if (id == null || workspaceId == null || subjectType == null || subjectId == null || phenomenon == null || valueJson == null || sourceType == null || observedAt == null) throw new NullPointerException();
    if (qualityScore < 0 || qualityScore > 1) throw new IllegalArgumentException("qualityScore must be 0..1");
  }
}
