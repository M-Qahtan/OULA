package com.oula.platform.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        String traceId,
        Instant timestamp,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String code, String message) {}
}
