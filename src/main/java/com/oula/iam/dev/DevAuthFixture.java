package com.oula.iam.dev;

import java.util.List;
import java.util.UUID;

record DevAuthFixture(
        String alias,
        UUID actorId,
        UUID workspaceId,
        String workspaceName,
        List<String> purposes,
        List<String> scopes
) {
    DevAuthFixture {
        purposes = List.copyOf(purposes);
        scopes = List.copyOf(scopes);
    }
}
