package com.oula.operations;

import java.util.List;
import java.util.UUID;

public record ManagementOverview(
        UUID propertyId,
        ManagementEnrollment enrollment,
        List<Obligation> obligations,
        List<GuardianSignal> guardianSignals,
        List<ActionItem> actions
) {
    public ManagementOverview {
        obligations = List.copyOf(obligations);
        guardianSignals = List.copyOf(guardianSignals);
        actions = List.copyOf(actions);
    }
}
