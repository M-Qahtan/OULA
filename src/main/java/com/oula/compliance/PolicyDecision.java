package com.oula.compliance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record PolicyDecision(UUID id, UUID workspaceId, UUID actorId, String action, String resourceType, UUID resourceId, Decision decision, List<String> reasonCodes, Instant evaluatedAt) {
  public enum Decision { ALLOW, DENY, REQUIRE_APPROVAL, REQUIRE_DOCUMENT, REQUIRE_VERIFICATION, ESCALATE }
  public PolicyDecision {
    if (id == null || workspaceId == null || actorId == null || action == null || resourceType == null || resourceId == null || decision == null || evaluatedAt == null) throw new NullPointerException();
    reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
  }
}
