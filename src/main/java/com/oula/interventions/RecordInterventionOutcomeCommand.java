package com.oula.interventions;
import java.util.UUID;
public record RecordInterventionOutcomeCommand(
        UUID afterSnapshotId, UUID completedWorkOrderId, String observationNote
) {}
