package com.oula.compliance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PolicyRequest(
        String action,
        String resourceType,
        UUID resourceId,
        String jurisdiction,
        BigDecimal amount,
        String currency,
        boolean verificationPresent,
        boolean evidencePresent,
        UUID approvalRequestId,
        Map<String, Object> context
) {
    public PolicyRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
