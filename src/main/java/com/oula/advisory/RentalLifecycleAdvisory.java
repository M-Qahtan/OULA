package com.oula.advisory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only Guardian-compatible advice; no legal, banking or external actions.
 * Metrics are observations, not proof of non-payment or actual present occupancy.
 */
public record RentalLifecycleAdvisory(
        UUID workspaceId, UUID propertyId, String rulesVersion,
        Instant generatedAt, LocalDate asOfDate, String assessmentState,
        int totalUnits, int knownOccupancyUnits, int unknownOccupancyUnits,
        int activeLeasesReviewed, List<String> limitations,
        List<AdvisoryItem> recommendations
) {
    public RentalLifecycleAdvisory {
        limitations = List.copyOf(limitations);
        recommendations = List.copyOf(recommendations);
    }
}
