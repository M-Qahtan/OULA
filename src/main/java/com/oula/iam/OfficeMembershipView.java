package com.oula.iam;

import java.util.UUID;

public record OfficeMembershipView(
        UUID workspaceId,
        UUID actorId,
        String role,
        String status
) {}
