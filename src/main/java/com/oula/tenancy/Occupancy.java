package com.oula.tenancy;
import java.time.Instant;
import java.util.UUID;
public record Occupancy(UUID id, UUID workspaceId, UUID unitId, UUID leaseId,
                        Instant checkedInAt, UUID checkInEvidenceId,
                        Instant checkedOutAt, UUID checkOutEvidenceId,
                        UUID recordedBy) {}
