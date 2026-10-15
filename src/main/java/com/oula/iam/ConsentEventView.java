package com.oula.iam;

import java.time.Instant;

public record ConsentEventView(
        ConsentCode consentCode,
        ConsentDecision decision,
        String reason,
        String source,
        Instant occurredAt
) {
}
