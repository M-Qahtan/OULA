package com.oula.advisory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OperationalAdvisory(
        UUID workspaceId, UUID propertyId, UUID sourceSnapshotId,
        String snapshotPolicyKey, String snapshotPolicyVersion,
        String advisoryRulesVersion, String assessmentState, String observedTrend,
        Instant sourceAssessedAt, Instant generatedAt,
        List<String> limitations, List<AdvisoryItem> recommendations
) {
    public OperationalAdvisory {
        limitations = List.copyOf(limitations);
        recommendations = List.copyOf(recommendations);
    }
}
