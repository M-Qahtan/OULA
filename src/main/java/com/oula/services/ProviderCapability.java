package com.oula.services;

import java.time.Instant;
import java.util.UUID;

public record ProviderCapability(
        UUID id,
        UUID providerId,
        String category,
        String status,
        Instant createdAt
) {}
