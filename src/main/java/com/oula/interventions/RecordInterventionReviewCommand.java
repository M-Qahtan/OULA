package com.oula.interventions;
import java.util.UUID;
public record RecordInterventionReviewCommand(
        UUID sourceSnapshotId, String dimension, String actionCode,
        String decision, String rationale
) {}
