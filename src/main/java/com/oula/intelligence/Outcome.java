package com.oula.intelligence;
import java.time.Instant;
import java.util.UUID;
public record Outcome(UUID id, UUID workspaceId, UUID decisionId, String outcomeType, String expectedJson, String actualJson, Instant observedAt) {
  public Outcome {
    if (id == null || workspaceId == null || decisionId == null || outcomeType == null || expectedJson == null || actualJson == null || observedAt == null) throw new NullPointerException();
  }
}
