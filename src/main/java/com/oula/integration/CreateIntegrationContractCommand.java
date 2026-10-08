package com.oula.integration;

import java.time.Instant;
import java.util.Set;

public record CreateIntegrationContractCommand(
        String contractKey,
        String version,
        String direction,
        String purpose,
        String operation,
        String resourceType,
        Set<String> allowedDataClasses,
        Instant effectiveFrom,
        Instant effectiveUntil
) {}
