package com.oula.compliance;

import com.oula.iam.AccessPurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyDecision(
        UUID id,
        UUID workspaceId,
        UUID actorId,
        AccessPurpose purpose,
        String action,
        String resourceType,
        UUID resourceId,
        String jurisdiction,
        BigDecimal amount,
        String currency,
        UUID policyRuleId,
        UUID approvalRequestId,
        Decision decision,
        List<String> reasonCodes,
        Instant evaluatedAt
) {
    public enum Decision {
        ALLOW,
        DENY,
        REQUIRE_APPROVAL,
        REQUIRE_DOCUMENT,
        REQUIRE_VERIFICATION,
        ESCALATE
    }

    public PolicyDecision {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }
}
