package com.oula.operations;
import java.time.Instant;
import java.util.UUID;
public record GuardianSignal(UUID id, UUID workspaceId, UUID propertyId, String signalType, String severity, String status, Instant detectedAt) {
  public GuardianSignal {
    if (id == null || workspaceId == null || propertyId == null || signalType == null || severity == null || status == null || detectedAt == null) throw new NullPointerException();
  }
}
