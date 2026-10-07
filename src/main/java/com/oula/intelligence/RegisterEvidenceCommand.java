package com.oula.intelligence;

import java.time.Instant;

public record RegisterEvidenceCommand(
        EvidenceType evidenceType,
        String sourceType,
        String sourceIdentity,
        String contentReference,
        String contentHash,
        VerificationStatus verificationStatus,
        Instant capturedAt,
        Instant validUntil,
        String jurisdiction
) {
}
