package com.oula.property;

import java.util.Map;
import java.util.UUID;

public record RecordPropertyFactCommand(
        String factKey,
        Map<String, Object> value,
        TruthStatus truthStatus,
        String sourceType,
        Double confidence,
        String visibility,
        UUID evidenceReference
) {
}
