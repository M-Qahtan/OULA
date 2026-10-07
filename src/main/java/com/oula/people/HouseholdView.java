package com.oula.people;

import java.util.UUID;

public record HouseholdView(
        UUID householdId,
        UUID workspaceId,
        String name,
        String status
) {
}
