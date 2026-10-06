package com.oula.intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Canonical recommendation envelope. Recommendation is never a binding decision. */
public record RecommendationContract(
        UUID id,
        String question,
        String recommendedAlternative,
        List<String> supportingFactRefs,
        List<String> evidenceRefs,
        List<String> assumptions,
        List<String> risks,
        List<String> tradeoffs,
        List<String> alternatives,
        double confidence,
        AuthorityRequirement authorityRequirement,
        Instant generatedAt,
        Instant validUntil
) {}
