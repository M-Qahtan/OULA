package com.oula.compliance;

import com.oula.iam.AccessPurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PolicyRule(
        UUID id,
        UUID workspaceId,
        String policyKey,
        String version,
        AccessPurpose appliesPurpose,
        String actionPattern,
        String resourceTypePattern,
        String jurisdictionPattern,
        PolicyDecision.Decision ruleEffect,
        BigDecimal maxAmount,
        String currency,
        boolean requiresVerification,
        boolean requiresEvidence,
        int priority,
        String status,
        Instant effectiveFrom,
        Instant effectiveUntil,
        UUID createdBy,
        Instant createdAt
) {}
