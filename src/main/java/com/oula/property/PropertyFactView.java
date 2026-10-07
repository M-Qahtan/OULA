package com.oula.property;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PropertyFactView(
        UUID factId,
        UUID propertyId,
        String factKey,
        Map<String, Object> value,
        TruthStatus truthStatus,
        String sourceType,
        Double confidence,
        String visibility,
        UUID evidenceReference,
        UUID verifiedBy,
        Instant verifiedAt,
        long version
) {
}
