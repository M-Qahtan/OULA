package com.oula.people;

import java.util.UUID;

public record PersonView(
        UUID personId,
        UUID workspaceId,
        String linkedSubject,
        String displayName,
        String status
) {
}
