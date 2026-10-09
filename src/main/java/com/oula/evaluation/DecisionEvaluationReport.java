package com.oula.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Descriptive evidence coverage only: no causal effect, validated accuracy,
 * financial recovery, or autonomous-learning claim.
 */
public record DecisionEvaluationReport(
        UUID workspaceId, UUID propertyId, String protocolVersion,
        Instant evaluatedAt, String evidenceAssessment,
        String operationalSourceStatus, RentalEvidence rental,
        OperationalEvidence operational, List<String> limitations,
        List<String> researchPrerequisites, boolean automaticTrainingAllowed
) {
    public DecisionEvaluationReport {
        limitations = List.copyOf(limitations);
        researchPrerequisites = List.copyOf(researchPrerequisites);
    }

    public record RentalEvidence(
            long capturedReviews, long reviewsWithHumanDecision,
            long reviewsWithDocumentaryObservation, long improvementLabels,
            long inconclusiveLabels, BigDecimal observationCoverageOfCaptured
    ) {}

    /** Null counts indicate missing operational source, never a fabricated zero. */
    public record OperationalEvidence(
            Long recordedReviews, Long followUpObservations,
            Long comparableStatusObservations, Long evidenceBackedWorkOrders,
            Long observedImprovements, BigDecimal followUpCoverageOfReviewed
    ) {}
}
