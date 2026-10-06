package com.oula.documents;
import java.time.Instant;
import java.util.UUID;
public record EvidenceRef(UUID evidenceId, UUID workspaceId, String evidenceType, String source, String verificationStatus, String contentHash, Instant capturedAt) {
  public EvidenceRef {
    if (evidenceId == null || workspaceId == null || evidenceType == null || source == null || verificationStatus == null || capturedAt == null) throw new NullPointerException();
  }
}
