package com.oula.decision;
import java.time.Instant;
import java.util.UUID;
public record DecisionCase(UUID id, UUID workspaceId, UUID intentId, String status, Instant createdAt) {
  public DecisionCase { if(id==null||workspaceId==null||intentId==null||status==null||createdAt==null)throw new NullPointerException(); }
}
