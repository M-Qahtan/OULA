package com.oula.vitals;

import java.util.List;
import java.util.UUID;

/**
 * Descriptive comparison of two immutable observations.
 * This is not a forecast, causal attribution, or autonomous action.
 */
public record PropertyVitalTrend(
        UUID propertyId,
        String direction,
        UUID currentSnapshotId,
        UUID previousSnapshotId,
        List<DimensionChange> dimensions
) {
    public PropertyVitalTrend {
        dimensions = List.copyOf(dimensions);
    }

    public record DimensionChange(
            String dimension,
            VitalStatus previous,
            VitalStatus current,
            String direction
    ) {}
}
