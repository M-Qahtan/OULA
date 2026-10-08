package com.oula.integration;

import java.time.Instant;
import java.util.UUID;

public record VerifiedExternalPrincipal(
        UUID workspaceId,
        UUID partnerId,
        String partnerCode,
        String authMode,
        String credentialReference,
        Instant authenticatedAt
) {}
