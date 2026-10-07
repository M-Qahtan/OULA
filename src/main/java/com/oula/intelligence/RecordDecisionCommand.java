package com.oula.intelligence;

import java.util.UUID;

public record RecordDecisionCommand(
        UUID recommendationId,
        UUID selectedPropertyId,
        String overrideReason
) {
}
