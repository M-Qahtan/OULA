package com.oula.compliance;

import java.util.Map;
import java.util.UUID;

public record PolicyEnforcementContext(
        String jurisdiction,
        UUID approvalRequestId,
        Map<String, Object> context
) {
    public PolicyEnforcementContext {
        jurisdiction = jurisdiction == null || jurisdiction.isBlank()
                ? "UNSPECIFIED"
                : jurisdiction.trim().toUpperCase();
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public static PolicyEnforcementContext unspecified() {
        return new PolicyEnforcementContext("UNSPECIFIED", null, Map.of());
    }
}
