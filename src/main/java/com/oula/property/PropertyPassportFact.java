package com.oula.property;

import java.time.Instant;
import java.util.UUID;

public record PropertyPassportFact(
        UUID id,
        String key,
        String valueJson,
        String truthStatus,
        String sourceType,
        Double confidence,
        Instant validFrom,
        Instant validTo
) {}
