package com.oula.intelligence;
import com.oula.matching.MatchRunOutcome;
import java.util.UUID;
public interface IntelligenceKernel {
  Recommendation recommend(UUID workspaceId, UUID intentId, MatchRunOutcome matches, UUID correlationId);
  DecisionRecord recordDecision(Recommendation recommendation, UUID selectedPropertyId, UUID decisionMaker, String overrideReason);
  Outcome observeOutcome(DecisionRecord decision, String outcomeType, String expectedJson, String actualJson);
}
