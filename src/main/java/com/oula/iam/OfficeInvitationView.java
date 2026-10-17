package com.oula.iam;

import java.time.Instant;
import java.util.UUID;

/** A membership invitation is NOT an active office role until recipient acceptance. */
public record OfficeInvitationView(
        UUID workspaceId, UUID actorId, String role, String status,
        long version, Instant expiresAt
) {}
