package com.oula.advisory;

import com.oula.vitals.VitalStatus;
import java.util.List;
import java.util.Objects;

public record AdvisoryItem(
        String dimension, VitalStatus observedStatus, String trendDirection,
        String priority, String actionCode, String explanation,
        String executionGate, List<EvidenceMetric> observedEvidence
) {
    public AdvisoryItem {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(observedStatus);
        Objects.requireNonNull(actionCode);
        observedEvidence = List.copyOf(observedEvidence);
    }

    public record EvidenceMetric(String name, String value) {
        public EvidenceMetric {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }
}
