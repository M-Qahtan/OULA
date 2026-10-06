package com.oula.intelligence;

import com.oula.iam.AccessContext;
import com.oula.matching.MatchAlternativeDecisionView;
import com.oula.matching.MatchRunDecisionQuery;
import com.oula.matching.MatchRunDecisionView;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DecisionIntelligenceService {
    private final IntelligenceRepository repository;
    private final MatchRunDecisionQuery matchRuns;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public DecisionIntelligenceService(
            IntelligenceRepository repository,
            MatchRunDecisionQuery matchRuns,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.matchRuns = matchRuns;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public UUID registerEvidence(
            AccessContext access,
            RegisterEvidenceCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.evidenceType(), "evidenceType");
        Objects.requireNonNull(command.verificationStatus(), "verificationStatus");
        requireText(command.sourceType(), "sourceType");

        if (isBlank(command.contentReference())
                && isBlank(command.contentHash())
                && isBlank(command.sourceIdentity())) {
            throw new IllegalArgumentException(
                    "evidence requires contentReference, contentHash, or sourceIdentity"
            );
        }

        UUID evidenceId = UuidV7.next();
        repository.insertEvidence(evidenceId, access.workspaceId(), command);

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_EVIDENCE_REGISTERED",
                "Evidence",
                evidenceId,
                correlationId,
                Map.of(
                        "evidenceType", command.evidenceType().name(),
                        "verificationStatus", command.verificationStatus().name()
                )
        );

        outbox.append(
                "intelligence.evidence.recorded.v1",
                "Evidence",
                evidenceId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "evidenceId", evidenceId,
                        "evidenceType", command.evidenceType().name(),
                        "verificationStatus", command.verificationStatus().name()
                )
        );

        return evidenceId;
    }

    @Transactional
    public UUID registerAssumption(
            AccessContext access,
            RegisterAssumptionCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");
        requireText(command.statement(), "statement");
        requireText(command.source(), "source");
        requireText(command.reason(), "reason");
        Objects.requireNonNull(command.sensitivity(), "sensitivity");
        requireUnitInterval(command.confidence(), "confidence");

        UUID assumptionId = UuidV7.next();
        repository.insertAssumption(
                assumptionId,
                access.workspaceId(),
                access.actorId(),
                command
        );

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_ASSUMPTION_REGISTERED",
                "Assumption",
                assumptionId,
                correlationId,
                Map.of(
                        "sensitivity", command.sensitivity().name(),
                        "confidence", command.confidence()
                )
        );

        outbox.append(
                "intelligence.assumption.registered.v1",
                "Assumption",
                assumptionId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "assumptionId", assumptionId,
                        "sensitivity", command.sensitivity().name()
                )
        );

        return assumptionId;
    }

    @Transactional
    public RecommendationView generateRecommendation(
            AccessContext access,
            GenerateRecommendationCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");

        if (command.validHours() < 1 || command.validHours() > 720) {
            throw new IllegalArgumentException("validHours must be between 1 and 720");
        }
        if (command.evidenceIds().isEmpty()) {
            throw new IllegalArgumentException("material recommendation requires evidence");
        }
        if (command.evidenceIds().size() > 25 || command.assumptionIds().size() > 25) {
            throw new IllegalArgumentException("too many evidence or assumption references");
        }

        repository.requireEvidence(access.workspaceId(), command.evidenceIds());
        repository.requireAssumptions(access.workspaceId(), command.assumptionIds());

        MatchRunDecisionView run = matchRuns.loadCompleted(
                command.matchRunId(),
                access.workspaceId()
        );
        ModelVersionRow model = repository.activeModel(run.algorithmVersion());
        MatchAlternativeDecisionView recommended = run.recommended();

        UUID recommendationId = UuidV7.next();
        Instant generatedAt = clock.instant();
        Instant validUntil = generatedAt.plusSeconds(command.validHours() * 3600L);

        Map<String, Object> confidenceBreakdown = new LinkedHashMap<>();
        confidenceBreakdown.put("matchResultConfidence", round(recommended.confidence()));
        confidenceBreakdown.put("evidenceCount", command.evidenceIds().size());
        confidenceBreakdown.put("assumptionCount", command.assumptionIds().size());
        confidenceBreakdown.put("basis", "lifefit-v1 truth-aware match result");

        Map<String, Object> uncertainty = new LinkedHashMap<>();
        uncertainty.put("epistemicGap", round(1.0 - recommended.confidence()));
        uncertainty.put("assumptionCount", command.assumptionIds().size());
        uncertainty.put(
                "knownLimitation",
                "LifeFit confidence currently reflects available property-truth coverage; it is not a probability of transaction success"
        );

        String reasoningSummary = """
                Property %s is ranked first by %s with LifeFit %.2f/100 and confidence %.2f.
                The recommendation is decision support, not an autonomous decision.
                """.formatted(
                recommended.propertyId(),
                run.algorithmVersion(),
                recommended.lifeFitScore(),
                recommended.confidence()
        ).trim();

        repository.insertRecommendation(
                recommendationId,
                access.workspaceId(),
                run.intentId(),
                run.matchRunId(),
                model,
                recommended.propertyId(),
                reasoningSummary,
                recommended.lifeFitScore(),
                recommended.confidence(),
                confidenceBreakdown,
                uncertainty,
                generatedAt,
                validUntil,
                correlationId,
                run.alternatives(),
                command.evidenceIds(),
                command.assumptionIds()
        );

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_RECOMMENDATION_GENERATED",
                "Recommendation",
                recommendationId,
                correlationId,
                Map.of(
                        "matchRunId", run.matchRunId(),
                        "modelKey", model.modelKey(),
                        "modelVersion", model.version(),
                        "riskClass", model.riskClass(),
                        "recommendedPropertyId", recommended.propertyId()
                )
        );

        outbox.append(
                "intelligence.recommendation.generated.v1",
                "Recommendation",
                recommendationId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "recommendationId", recommendationId,
                        "matchRunId", run.matchRunId(),
                        "recommendedPropertyId", recommended.propertyId(),
                        "modelKey", model.modelKey(),
                        "modelVersion", model.version(),
                        "riskClass", model.riskClass()
                )
        );

        return new RecommendationView(
                recommendationId,
                run.matchRunId(),
                recommended.propertyId(),
                recommended.lifeFitScore(),
                recommended.confidence(),
                model.modelKey(),
                model.version(),
                command.evidenceIds(),
                command.assumptionIds()
        );
    }

    @Transactional
    public DecisionView recordDecision(
            AccessContext access,
            RecordDecisionCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.selectedPropertyId(), "selectedPropertyId");

        RecommendationRow recommendation = repository.recommendation(
                command.recommendationId(),
                access.workspaceId()
        );

        if (!"GENERATED".equals(recommendation.status())) {
            throw new IllegalStateException("recommendation is not active");
        }
        if (recommendation.validUntil() != null
                && recommendation.validUntil().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalStateException("recommendation has expired");
        }

        repository.requireAlternative(
                recommendation.id(),
                command.selectedPropertyId()
        );

        boolean acceptedRecommendation =
                command.selectedPropertyId().equals(recommendation.recommendedPropertyId());

        if (!acceptedRecommendation && isBlank(command.overrideReason())) {
            throw new IllegalArgumentException(
                    "overrideReason is required when the human selects another alternative"
            );
        }

        UUID decisionId = UuidV7.next();
        Instant decidedAt = clock.instant();

        repository.insertDecision(
                decisionId,
                access.workspaceId(),
                recommendation.id(),
                command.selectedPropertyId(),
                access.actorId(),
                acceptedRecommendation,
                command.overrideReason(),
                decidedAt,
                correlationId
        );

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_DECISION_RECORDED",
                "DecisionRecord",
                decisionId,
                correlationId,
                Map.of(
                        "recommendationId", recommendation.id(),
                        "selectedPropertyId", command.selectedPropertyId(),
                        "acceptedRecommendation", acceptedRecommendation
                )
        );

        outbox.append(
                "intelligence.decision.recorded.v1",
                "DecisionRecord",
                decisionId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "decisionId", decisionId,
                        "recommendationId", recommendation.id(),
                        "selectedPropertyId", command.selectedPropertyId(),
                        "acceptedRecommendation", acceptedRecommendation
                )
        );

        return new DecisionView(
                decisionId,
                recommendation.id(),
                command.selectedPropertyId(),
                acceptedRecommendation
        );
    }

    @Transactional
    public OutcomeView recordOutcome(
            AccessContext access,
            RecordOutcomeCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");

        if (command.actualCommuteMinutes() < 0) {
            throw new IllegalArgumentException("actualCommuteMinutes must be non-negative");
        }
        if (command.satisfactionScore() < 0 || command.satisfactionScore() > 100) {
            throw new IllegalArgumentException("satisfactionScore must be between 0 and 100");
        }
        requireUnitInterval(command.confidence(), "confidence");
        if (command.evidenceIds().isEmpty()) {
            throw new IllegalArgumentException("outcome requires evidence");
        }
        if (command.evidenceIds().size() > 25) {
            throw new IllegalArgumentException("too many evidence references");
        }

        repository.requireEvidence(access.workspaceId(), command.evidenceIds());
        DecisionRow decision = repository.decision(
                command.decisionId(),
                access.workspaceId()
        );
        AlternativeRow selected = repository.requireAlternative(
                decision.recommendationId(),
                decision.selectedPropertyId()
        );

        Instant observedAt = command.observedAt() == null
                ? clock.instant()
                : command.observedAt();

        Map<String, Object> expected = new LinkedHashMap<>();
        if (selected.expectedCommuteMinutes() != null) {
            expected.put("commuteMinutes", selected.expectedCommuteMinutes());
        }

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("commuteMinutes", command.actualCommuteMinutes());
        actual.put("satisfactionScore", round(command.satisfactionScore()));

        Map<String, Object> variance = new LinkedHashMap<>();
        if (selected.expectedCommuteMinutes() != null) {
            variance.put(
                    "commuteMinutes",
                    command.actualCommuteMinutes() - selected.expectedCommuteMinutes()
            );
        }

        UUID observationId = UuidV7.next();
        UUID outcomeId = UuidV7.next();

        repository.insertObservation(
                observationId,
                access.workspaceId(),
                decision.selectedPropertyId(),
                command.actualCommuteMinutes(),
                observedAt,
                command.evidenceIds().getFirst(),
                command.confidence()
        );

        repository.insertOutcome(
                outcomeId,
                access.workspaceId(),
                decision.id(),
                observationId,
                expected,
                actual,
                variance,
                command.confidence(),
                observedAt,
                correlationId,
                command.evidenceIds()
        );

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_OUTCOME_RECORDED",
                "Outcome",
                outcomeId,
                correlationId,
                Map.of(
                        "decisionId", decision.id(),
                        "observationId", observationId,
                        "selectedPropertyId", decision.selectedPropertyId()
                )
        );

        outbox.append(
                "intelligence.outcome.observed.v1",
                "Outcome",
                outcomeId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "outcomeId", outcomeId,
                        "decisionId", decision.id(),
                        "observationId", observationId,
                        "expectedMetrics", expected,
                        "actualMetrics", actual,
                        "varianceMetrics", variance
                )
        );

        return new OutcomeView(
                outcomeId,
                decision.id(),
                observationId,
                expected,
                actual,
                variance
        );
    }

    @Transactional(readOnly = true)
    public RecommendationExplanation explain(
            AccessContext access,
            UUID recommendationId
    ) {
        requireAccess(access);
        RecommendationRow recommendation = repository.recommendation(
                recommendationId,
                access.workspaceId()
        );
        List<UUID> evidenceIds = repository.recommendationEvidence(recommendationId);
        List<UUID> assumptionIds = repository.recommendationAssumptions(recommendationId);
        List<RecommendationExplanation.AlternativeExplanation> alternatives =
                repository.alternatives(recommendationId).stream()
                        .map(alternative -> new RecommendationExplanation.AlternativeExplanation(
                                alternative.propertyId(),
                                alternative.rank(),
                                alternative.lifeFitScore(),
                                alternative.confidence(),
                                alternative.dimensions(),
                                alternative.expectedCommuteMinutes()
                        ))
                        .toList();

        return new RecommendationExplanation(
                recommendation.id(),
                recommendation.reasoningSummary(),
                recommendation.recommendedPropertyId(),
                recommendation.lifeFitScore(),
                recommendation.confidence(),
                recommendation.confidenceBreakdown(),
                recommendation.uncertainty(),
                recommendation.modelKey(),
                recommendation.modelVersion(),
                recommendation.modelType(),
                recommendation.riskClass(),
                evidenceIds,
                assumptionIds,
                alternatives
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        Objects.requireNonNull(access.purpose(), "purpose");
    }

    private void requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requireUnitInterval(double value, String field) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
