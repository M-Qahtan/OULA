package com.oula.iam;

import java.util.UUID;

public record AccessContext(
        UUID actorId,
        String subject,
        UUID workspaceId,
        AccessPurpose purpose
) {
}
