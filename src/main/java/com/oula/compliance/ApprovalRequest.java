package com.oula.compliance;

import com.oula.iam.AccessPurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalRequest(
        UUID id,
        UUID workspaceId,
        UUID policyRuleId,
        UUID policyDecisionId,
        UUID requestedBy,
        AccessPurpose purpose,
        String action,
        String resourceType,
        UUID resourceId,
        String jurisdiction,
        BigDecimal amount,
        String currency,
        String justification,
        String status,
        Instant requestedAt,
        Instant expiresAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote,
        long version
) {}
