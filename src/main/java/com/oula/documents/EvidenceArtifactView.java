package com.oula.documents;

import java.time.Instant;
import java.util.UUID;

public record EvidenceArtifactView(
        UUID evidenceId,
        UUID workspaceId,
        String evidenceType,
        String source,
        String verificationStatus,
        String contentHash,
        Instant capturedAt,
        UUID verifiedBy,
        Instant verifiedAt,
        String verificationReason
) {
}
