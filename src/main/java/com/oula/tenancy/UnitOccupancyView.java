package com.oula.tenancy;
import java.time.Instant;
import java.util.UUID;

/** Evidence-recorded possession status, never a live sensor assertion. */
public record UnitOccupancyView(UUID unitId, UUID propertyId, String unitCode,
                                String occupancyStatus, UUID leaseId,
                                Instant lastCheckedInAt, Instant lastCheckedOutAt) {}
