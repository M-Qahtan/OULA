package com.oula.intelligence;

import java.time.Instant;
import java.util.Map;

public record RegisterAssumptionCommand(
        String statement,
        Map<String, Object> value,
        String source,
        String reason,
        double confidence,
        AssumptionSensitivity sensitivity,
        Instant validUntil
) {
    public RegisterAssumptionCommand {
        value = value == null ? Map.of() : Map.copyOf(value);
    }
}
