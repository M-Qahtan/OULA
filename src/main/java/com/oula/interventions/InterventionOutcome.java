package com.oula.interventions;

import com.oula.vitals.VitalStatus;
import java.time.Instant;
import java.util.UUID;

public record InterventionOutcome(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID reviewId,
        UUID afterSnapshotId,
        UUID workOrderId,
        VitalStatus beforeStatus,
        VitalStatus afterStatus,
        String observedDirection,
        String executionEvidenceLevel,
        String note,
        UUID observedBy,
        Instant observedAt
) {}
