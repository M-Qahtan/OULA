package com.oula.integration;

import java.time.Instant;
import java.util.UUID;

public record IntegrationPartner(
        UUID id,
        UUID workspaceId,
        String partnerCode,
        String partnerType,
        String displayName,
        String jurisdiction,
        String status,
        String verificationStatus,
        UUID verificationEvidenceId,
        Instant verifiedAt,
        String authMode,
        String credentialReference,
        boolean inboundEnabled,
        boolean outboundEnabled,
        UUID createdBy,
        Instant createdAt,
        long version
) {}
