package com.oula.operations;

import java.time.Instant;
import java.util.UUID;

public record GuardianAssessment(
        UUID propertyId,
        Instant assessedAt,
        int obligationsEvaluated,
        int signalsCreated,
        int actionsCreated
) {}
