package com.oula.intelligence;
import java.util.UUID;
public record Assumption(UUID id, UUID workspaceId, String statement, String valueJson, String source, double confidence, String sensitivity) {
  public Assumption {
    if (id == null || workspaceId == null || statement == null || valueJson == null || source == null || sensitivity == null) throw new NullPointerException();
    if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be 0..1");
  }
}
