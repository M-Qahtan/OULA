package com.oula.intelligence;

import com.oula.matching.MatchAlternativeDecisionView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class IntelligenceRepository {
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    IntelligenceRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    void insertEvidence(UUID id, UUID workspaceId, RegisterEvidenceCommand command) {
        jdbc.sql("""
                insert into intelligence.evidence
                    (id, workspace_id, evidence_type, source_type, source_identity,
                     content_reference, content_hash, verification_status, captured_at,
                     valid_until, jurisdiction)
                values
                    (:id, :workspaceId, :evidenceType, :sourceType, :sourceIdentity,
                     :contentReference, :contentHash, :verificationStatus, :capturedAt,
                     :validUntil, :jurisdiction)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("evidenceType", command.evidenceType().name())
                .param("sourceType", command.sourceType())
                .param("sourceIdentity", command.sourceIdentity())
                .param("contentReference", command.contentReference())
                .param("contentHash", command.contentHash())
                .param("verificationStatus", command.verificationStatus().name())
                .param("capturedAt", utc(command.capturedAt()))
                .param("validUntil", utc(command.validUntil()))
                .param("jurisdiction", command.jurisdiction())
                .update();
    }

    void insertAssumption(
            UUID id,
            UUID workspaceId,
            UUID actorId,
            RegisterAssumptionCommand command
    ) {
        jdbc.sql("""
                insert into intelligence.assumption
                    (id, workspace_id, statement, value_json, source, reason,
                     confidence, sensitivity, valid_until, created_by)
                values
                    (:id, :workspaceId, :statement, cast(:value as jsonb), :source, :reason,
                     :confidence, :sensitivity, :validUntil, :createdBy)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("statement", command.statement())
                .param("value", json(command.value()))
                .param("source", command.source())
                .param("reason", command.reason())
                .param("confidence", command.confidence())
                .param("sensitivity", command.sensitivity().name())
                .param("validUntil", utc(command.validUntil()))
                .param("createdBy", actorId)
                .update();
    }

    void requireEvidence(UUID workspaceId, List<UUID> evidenceIds) {
        for (UUID evidenceId : evidenceIds) {
            Integer count = jdbc.sql("""
                    select count(*)
                      from intelligence.evidence
                     where id = :id
                       and workspace_id = :workspaceId
                       and verification_status <> 'REJECTED'
                    """)
                    .param("id", evidenceId)
                    .param("workspaceId", workspaceId)
                    .query(Integer.class)
                    .single();
            if (count == null || count != 1) {
                throw new NoSuchElementException("evidence not available in workspace: " + evidenceId);
            }
        }
    }

    void requireAssumptions(UUID workspaceId, List<UUID> assumptionIds) {
        for (UUID assumptionId : assumptionIds) {
            Integer count = jdbc.sql("""
                    select count(*)
                      from intelligence.assumption
                     where id = :id
                       and workspace_id = :workspaceId
                       and (valid_until is null or valid_until > now())
                    """)
                    .param("id", assumptionId)
                    .param("workspaceId", workspaceId)
                    .query(Integer.class)
                    .single();
            if (count == null || count != 1) {
                throw new NoSuchElementException("assumption not available in workspace: " + assumptionId);
            }
        }
    }

    ModelVersionRow activeModel(String modelKey) {
        return jdbc.sql("""
                select id, model_key, version, model_type, risk_class
                  from intelligence.model_version
                 where model_key = :modelKey
                   and status = 'ACTIVE'
                 order by released_at desc
                 limit 1
                """)
                .param("modelKey", modelKey)
                .query((rs, rowNum) -> new ModelVersionRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("model_key"),
                        rs.getString("version"),
                        rs.getString("model_type"),
                        rs.getString("risk_class")
                ))
                .optional()
                .orElseThrow(() -> new IllegalStateException("active model not registered: " + modelKey));
    }

    void insertRecommendation(
            UUID recommendationId,
            UUID workspaceId,
            UUID intentId,
            UUID matchRunId,
            ModelVersionRow model,
            UUID recommendedPropertyId,
            String reasoningSummary,
            double lifeFitScore,
            double confidence,
            Map<String, Object> confidenceBreakdown,
            Map<String, Object> uncertainty,
            Instant generatedAt,
            Instant validUntil,
            UUID correlationId,
            List<MatchAlternativeDecisionView> alternatives,
            List<UUID> evidenceIds,
            List<UUID> assumptionIds
    ) {
        jdbc.sql("""
                insert into intelligence.recommendation
                    (id, workspace_id, intent_id, match_run_id, model_version_id,
                     recommended_property_id, reasoning_summary, life_fit_score,
                     confidence, confidence_breakdown, uncertainty, status,
                     generated_at, valid_until, correlation_id)
                values
                    (:id, :workspaceId, :intentId, :matchRunId, :modelVersionId,
                     :recommendedPropertyId, :reasoningSummary, :lifeFitScore,
                     :confidence, cast(:confidenceBreakdown as jsonb), cast(:uncertainty as jsonb),
                     'GENERATED', :generatedAt, :validUntil, :correlationId)
                """)
                .param("id", recommendationId)
                .param("workspaceId", workspaceId)
                .param("intentId", intentId)
                .param("matchRunId", matchRunId)
                .param("modelVersionId", model.id())
                .param("recommendedPropertyId", recommendedPropertyId)
                .param("reasoningSummary", reasoningSummary)
                .param("lifeFitScore", lifeFitScore)
                .param("confidence", confidence)
                .param("confidenceBreakdown", json(confidenceBreakdown))
                .param("uncertainty", json(uncertainty))
                .param("generatedAt", utc(generatedAt))
                .param("validUntil", utc(validUntil))
                .param("correlationId", correlationId)
                .update();

        for (MatchAlternativeDecisionView alternative : alternatives) {
            jdbc.sql("""
                    insert into intelligence.recommendation_alternative
                        (recommendation_id, property_id, rank, life_fit_score,
                         confidence, explanation, expected_commute_minutes)
                    values
                        (:recommendationId, :propertyId, :rank, :lifeFitScore,
                         :confidence, cast(:explanation as jsonb), :expectedCommuteMinutes)
                    """)
                    .param("recommendationId", recommendationId)
                    .param("propertyId", alternative.propertyId())
                    .param("rank", alternative.rank())
                    .param("lifeFitScore", alternative.lifeFitScore())
                    .param("confidence", alternative.confidence())
                    .param("explanation", alternative.explanationJson())
                    .param("expectedCommuteMinutes", alternative.expectedCommuteMinutes())
                    .update();
        }

        for (UUID evidenceId : evidenceIds) {
            jdbc.sql("""
                    insert into intelligence.recommendation_evidence(recommendation_id, evidence_id)
                    values (:recommendationId, :evidenceId)
                    """)
                    .param("recommendationId", recommendationId)
                    .param("evidenceId", evidenceId)
                    .update();
        }

        for (UUID assumptionId : assumptionIds) {
            jdbc.sql("""
                    insert into intelligence.recommendation_assumption(recommendation_id, assumption_id)
                    values (:recommendationId, :assumptionId)
                    """)
                    .param("recommendationId", recommendationId)
                    .param("assumptionId", assumptionId)
                    .update();
        }
    }

    RecommendationRow recommendation(UUID recommendationId, UUID workspaceId) {
        return jdbc.sql("""
                select r.id,
                       r.workspace_id,
                       r.intent_id,
                       r.match_run_id,
                       r.recommended_property_id,
                       r.reasoning_summary,
                       r.life_fit_score,
                       r.confidence,
                       r.confidence_breakdown::text,
                       r.uncertainty::text,
                       r.status,
                       r.valid_until,
                       m.model_key,
                       m.version,
                       m.model_type,
                       m.risk_class
                  from intelligence.recommendation r
                  join intelligence.model_version m on m.id = r.model_version_id
                 where r.id = :recommendationId
                   and r.workspace_id = :workspaceId
                """)
                .param("recommendationId", recommendationId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new RecommendationRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("intent_id", UUID.class),
                        rs.getObject("match_run_id", UUID.class),
                        rs.getObject("recommended_property_id", UUID.class),
                        rs.getString("reasoning_summary"),
                        rs.getDouble("life_fit_score"),
                        rs.getDouble("confidence"),
                        map(rs.getString("confidence_breakdown")),
                        map(rs.getString("uncertainty")),
                        rs.getString("status"),
                        rs.getObject("valid_until", OffsetDateTime.class),
                        rs.getString("model_key"),
                        rs.getString("version"),
                        rs.getString("model_type"),
                        rs.getString("risk_class")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("recommendation not found"));
    }

    List<UUID> recommendationEvidence(UUID recommendationId) {
        return jdbc.sql("""
                select evidence_id
                  from intelligence.recommendation_evidence
                 where recommendation_id = :recommendationId
                 order by evidence_id
                """)
                .param("recommendationId", recommendationId)
                .query(UUID.class)
                .list();
    }

    List<UUID> recommendationAssumptions(UUID recommendationId) {
        return jdbc.sql("""
                select assumption_id
                  from intelligence.recommendation_assumption
                 where recommendation_id = :recommendationId
                 order by assumption_id
                """)
                .param("recommendationId", recommendationId)
                .query(UUID.class)
                .list();
    }

    List<AlternativeRow> alternatives(UUID recommendationId) {
        return jdbc.sql("""
                select property_id, rank, life_fit_score, confidence,
                       explanation::text, expected_commute_minutes
                  from intelligence.recommendation_alternative
                 where recommendation_id = :recommendationId
                 order by rank
                """)
                .param("recommendationId", recommendationId)
                .query((rs, rowNum) -> new AlternativeRow(
                        rs.getObject("property_id", UUID.class),
                        rs.getInt("rank"),
                        rs.getDouble("life_fit_score"),
                        rs.getDouble("confidence"),
                        map(rs.getString("explanation")),
                        (Integer) rs.getObject("expected_commute_minutes")
                ))
                .list();
    }

    AlternativeRow requireAlternative(UUID recommendationId, UUID propertyId) {
        return jdbc.sql("""
                select property_id, rank, life_fit_score, confidence,
                       explanation::text, expected_commute_minutes
                  from intelligence.recommendation_alternative
                 where recommendation_id = :recommendationId
                   and property_id = :propertyId
                """)
                .param("recommendationId", recommendationId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> new AlternativeRow(
                        rs.getObject("property_id", UUID.class),
                        rs.getInt("rank"),
                        rs.getDouble("life_fit_score"),
                        rs.getDouble("confidence"),
                        map(rs.getString("explanation")),
                        (Integer) rs.getObject("expected_commute_minutes")
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("selected property is not a recommendation alternative"));
    }

    void insertDecision(
            UUID decisionId,
            UUID workspaceId,
            UUID recommendationId,
            UUID selectedPropertyId,
            UUID decisionMaker,
            boolean acceptedRecommendation,
            String overrideReason,
            Instant decidedAt,
            UUID correlationId
    ) {
        jdbc.sql("""
                insert into intelligence.decision_record
                    (id, workspace_id, recommendation_id, selected_property_id,
                     decision_maker, accepted_recommendation, override_reason,
                     decided_at, correlation_id)
                values
                    (:id, :workspaceId, :recommendationId, :selectedPropertyId,
                     :decisionMaker, :acceptedRecommendation, :overrideReason,
                     :decidedAt, :correlationId)
                """)
                .param("id", decisionId)
                .param("workspaceId", workspaceId)
                .param("recommendationId", recommendationId)
                .param("selectedPropertyId", selectedPropertyId)
                .param("decisionMaker", decisionMaker)
                .param("acceptedRecommendation", acceptedRecommendation)
                .param("overrideReason", overrideReason)
                .param("decidedAt", utc(decidedAt))
                .param("correlationId", correlationId)
                .update();
    }

    DecisionRow decision(UUID decisionId, UUID workspaceId) {
        return jdbc.sql("""
                select d.id,
                       d.workspace_id,
                       d.recommendation_id,
                       d.selected_property_id,
                       d.accepted_recommendation,
                       r.intent_id
                  from intelligence.decision_record d
                  join intelligence.recommendation r on r.id = d.recommendation_id
                 where d.id = :decisionId
                   and d.workspace_id = :workspaceId
                """)
                .param("decisionId", decisionId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new DecisionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("recommendation_id", UUID.class),
                        rs.getObject("selected_property_id", UUID.class),
                        rs.getBoolean("accepted_recommendation"),
                        rs.getObject("intent_id", UUID.class)
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("decision not found"));
    }

    void insertObservation(
            UUID observationId,
            UUID workspaceId,
            UUID subjectId,
            int actualCommuteMinutes,
            Instant observedAt,
            UUID evidenceId,
            double qualityScore
    ) {
        jdbc.sql("""
                insert into intelligence.observation
                    (id, workspace_id, subject_type, subject_id, phenomenon,
                     value_json, unit, observed_at, source_type, method,
                     evidence_id, quality_score)
                values
                    (:id, :workspaceId, 'PROPERTY', :subjectId, 'ACTUAL_COMMUTE_MINUTES',
                     cast(:value as jsonb), 'MINUTES', :observedAt, 'USER_REPORTED_OUTCOME',
                     'POST_DECISION_FEEDBACK', :evidenceId, :qualityScore)
                """)
                .param("id", observationId)
                .param("workspaceId", workspaceId)
                .param("subjectId", subjectId)
                .param("value", json(Map.of("value", actualCommuteMinutes)))
                .param("observedAt", utc(observedAt))
                .param("evidenceId", evidenceId)
                .param("qualityScore", qualityScore)
                .update();
    }

    void insertOutcome(
            UUID outcomeId,
            UUID workspaceId,
            UUID decisionId,
            UUID observationId,
            Map<String, Object> expectedMetrics,
            Map<String, Object> actualMetrics,
            Map<String, Object> varianceMetrics,
            double confidence,
            Instant observedAt,
            UUID correlationId,
            List<UUID> evidenceIds
    ) {
        jdbc.sql("""
                insert into intelligence.outcome
                    (id, workspace_id, decision_id, observation_id,
                     expected_metrics, actual_metrics, variance_metrics,
                     confidence, observed_at, correlation_id)
                values
                    (:id, :workspaceId, :decisionId, :observationId,
                     cast(:expectedMetrics as jsonb), cast(:actualMetrics as jsonb),
                     cast(:varianceMetrics as jsonb), :confidence, :observedAt, :correlationId)
                """)
                .param("id", outcomeId)
                .param("workspaceId", workspaceId)
                .param("decisionId", decisionId)
                .param("observationId", observationId)
                .param("expectedMetrics", json(expectedMetrics))
                .param("actualMetrics", json(actualMetrics))
                .param("varianceMetrics", json(varianceMetrics))
                .param("confidence", confidence)
                .param("observedAt", utc(observedAt))
                .param("correlationId", correlationId)
                .update();

        for (UUID evidenceId : evidenceIds) {
            jdbc.sql("""
                    insert into intelligence.outcome_evidence(outcome_id, evidence_id)
                    values (:outcomeId, :evidenceId)
                    """)
                    .param("outcomeId", outcomeId)
                    .param("evidenceId", evidenceId)
                    .update();
        }
    }

    private String json(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize intelligence JSON", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(value, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize intelligence JSON", ex);
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

record ModelVersionRow(
        UUID id,
        String modelKey,
        String version,
        String modelType,
        String riskClass
) {
}

record RecommendationRow(
        UUID id,
        UUID workspaceId,
        UUID intentId,
        UUID matchRunId,
        UUID recommendedPropertyId,
        String reasoningSummary,
        double lifeFitScore,
        double confidence,
        Map<String, Object> confidenceBreakdown,
        Map<String, Object> uncertainty,
        String status,
        OffsetDateTime validUntil,
        String modelKey,
        String modelVersion,
        String modelType,
        String riskClass
) {
}

record AlternativeRow(
        UUID propertyId,
        int rank,
        double lifeFitScore,
        double confidence,
        Map<String, Object> dimensions,
        Integer expectedCommuteMinutes
) {
}

record DecisionRow(
        UUID id,
        UUID workspaceId,
        UUID recommendationId,
        UUID selectedPropertyId,
        boolean acceptedRecommendation,
        UUID intentId
) {
}
