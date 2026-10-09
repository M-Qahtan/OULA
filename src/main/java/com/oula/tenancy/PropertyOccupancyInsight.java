package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Coverage excludes UNKNOWN units, so unknown is never misreported as vacant. */
public record PropertyOccupancyInsight(
        UUID propertyId, Instant asOf, int totalUnits, int knownUnits,
        int occupiedRecordedUnits, int vacancyRecordedUnits, int unknownUnits,
        BigDecimal evidenceCoverageRatio, BigDecimal occupiedShareOfKnown,
        String coverageStatus
) {}
