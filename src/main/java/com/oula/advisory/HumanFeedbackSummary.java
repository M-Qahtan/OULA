package com.oula.advisory;
import java.util.UUID;

/**
 * Counts of recorded human dispositions and documentary outcomes.
 * No causal success rate or algorithmic efficacy claim is calculated.
 */
public record HumanFeedbackSummary(
        UUID propertyId, long capturedCases, long casesWithHumanDecision,
        long casesWithOutcomeObservation, long casesWithImprovementObservation,
        long casesWithInconclusiveObservation, String evidenceInterpretation
) {}
