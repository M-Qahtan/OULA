package com.oula.interventions;

import com.oula.vitals.VitalStatus;
import java.time.Instant;
import java.util.UUID;

public record InterventionReview(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID sourceSnapshotId,
        String sourcePolicyKey,
        String sourcePolicyVersion,
        String advisoryRulesVersion,
        String dimension,
        String actionCode,
        VitalStatus baselineStatus,
        String decision,
        String rationale,
        UUID reviewedBy,
        Instant reviewedAt
) {}
