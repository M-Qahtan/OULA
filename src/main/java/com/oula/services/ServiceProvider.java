package com.oula.services;

import java.time.Instant;
import java.util.UUID;

public record ServiceProvider(
        UUID id,
        UUID workspaceId,
        UUID providerPartyId,
        String displayName,
        String status,
        String verificationStatus,
        UUID verificationEvidenceId,
        Instant verifiedAt,
        Instant createdAt,
        long version
) {}
