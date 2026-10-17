package com.oula.transaction;

import java.time.Instant;
import java.util.UUID;

/** Non-sensitive grant metadata; possession of this response never authorizes a command. */
public record DealGrantSnapshot(
        UUID transactionId,
        UUID actorId,
        String role,
        boolean active,
        Instant expiresAt
) {}
