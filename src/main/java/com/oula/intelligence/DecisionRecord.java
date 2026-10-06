package com.oula.intelligence;
import java.time.Instant;
import java.util.UUID;
public record DecisionRecord(UUID id, UUID workspaceId, UUID recommendationId, UUID selectedPropertyId, UUID decisionMaker, boolean acceptedRecommendation, String overrideReason, Instant decidedAt) {
  public DecisionRecord {
    if (id == null || workspaceId == null || recommendationId == null || selectedPropertyId == null || decisionMaker == null || decidedAt == null) throw new NullPointerException();
  }
}
