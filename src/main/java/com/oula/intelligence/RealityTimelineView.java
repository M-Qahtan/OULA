package com.oula.intelligence;

import java.util.List;

/**
 * The same workspace-scoped RealityCase with a reproducible temporal read model.
 */
public record RealityTimelineView(
        RealityCaseView realityCase,
        List<RealityTimelineEvent> events
) {
    public RealityTimelineView {
        events = List.copyOf(events);
    }
}
