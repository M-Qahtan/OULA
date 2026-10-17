package com.oula.iam;

import java.time.Instant;
import java.util.UUID;

public record OfficeMemberInvitationView(
        UUID invitationId,
        UUID workspaceId,
        UUID actorId,
        String role,
        String status,
        Instant expiresAt
) {}
