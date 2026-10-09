package com.oula.interventions;
import java.util.List;
import java.util.UUID;
public record InterventionHistory(
        UUID propertyId, List<InterventionReview> reviews,
        List<InterventionOutcome> outcomes
) {
    public InterventionHistory {
        reviews = List.copyOf(reviews);
        outcomes = List.copyOf(outcomes);
    }
}
