package com.oula.compliance;

import com.oula.iam.AccessPurpose;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePolicyRuleCommand(
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
        Instant effectiveFrom,
        Instant effectiveUntil
) {}
