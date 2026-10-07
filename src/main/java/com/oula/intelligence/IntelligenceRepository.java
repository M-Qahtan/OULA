package com.oula.intelligence;

import com.oula.matching.MatchAlternativeDecisionView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    void insertAssumption(
            UUID id,
            UUID workspaceId,
            UUID actorId,
            RegisterAssumptionCommand command
    ) {
        jdbc.sql("""
                insert into intelligence.assumption
                    (id, workspace_id, statement, value_json, source, confidence,
                     sensitivity, reason, valid_until, created_by)
                values
                    (:id, :workspaceId, :statement, cast(:value as jsonb), :source,
                     :confidence, :sensitivity, :reason, :validUntil, :createdBy)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("statement", command.statement())
                .param("value", json(command.value()))
                .param("source", command.source())
                .param("confidence", command.confidence())
                .param("sensitivity", command.sensitivity().name())
                .param("reason", command.reason())
                .param("validUntil", utc(command.validUntil()))
                .param("createdBy", actorId)
                .update();
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
                throw new NoSuchElementException(
                        "assumption not available in workspace: " + assumptionId
                );
            }
        }
    }

    ModelVersionRow activeModelByRuntimeKey(String runtimeKey) {
        return jdbc.sql("""
                select id, model_id, version, model_type, risk_class
                  from intelligence.model_version
                 where runtime_key = :runtimeKey
                   and status = 'ACTIVE'
                 limit 1
                """)
                .param("runtimeKey", runtimeKey)
                .query((rs, rowNum) -> new ModelVersionRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("model_id"),
                        rs.getString("version"),
                        rs.getString("model_type"),
                        rs.getString("risk_class")
                ))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "active model not registered for runtime key: " + runtimeKey
                ));
    }

    void enrichRecommendation(
            UUID recommendationId,
            UUID matchRunId,
            ModelVersionRow model,
            String reasoningSummary,
            double lifeFitScore,
            Map<String, Object> confidenceBreakdown,
            Map<String, Object> uncertainty,
            Instant validUntil,
            List<MatchAlternativeDecisionView> alternatives,
            List<UUID> evidenceIds,
            List<UUID> assumptionIds
    ) {
        int updated = jdbc.sql("""
                update intelligence.recommendation
                   set match_run_id = :matchRunId,
                       model_version_id = :modelVersionId,
                       reasoning_summary = :reasoningSummary,
                       life_fit_score = :lifeFitScore,
                       confidence_breakdown = cast(:confidenceBreakdown as jsonb),
                       uncertainty = cast(:uncertainty as jsonb),
                       status = 'GENERATED',
                       valid_until = :validUntil
                 where id = :recommendationId
                """)
                .param("recommendationId", recommendationId)
                .param("matchRunId", matchRunId)
                .param("modelVersionId", model.id())
                .param("reasoningSummary", reasoningSummary)
                .param("lifeFitScore", lifeFitScore)
                .param("confidenceBreakdown", json(confidenceBreakdown))
                .param("uncertainty", json(uncertainty))
                .param("validUntil", utc(validUntil))
                .update();

        if (updated != 1) {
            throw new IllegalStateException("recommendation enrichment failed");
        }

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
                       m.model_id,
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
                        rs.getString("model_id"),
                        rs.getString("version"),
                        rs.getString("model_type"),
                        rs.getString("risk_class")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException(
                        "operational recommendation not found"
                ));
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
                .query((rs, rowNum) -> alternativeRow(rs))
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
                .query((rs, rowNum) -> alternativeRow(rs))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "selected property is not a recommendation alternative"
                ));
    }

    void attachDecisionCorrelation(UUID decisionId, UUID correlationId) {
        int updated = jdbc.sql("""
                update intelligence.decision_record
                   set correlation_id = :correlationId
                 where id = :decisionId
                """)
                .param("decisionId", decisionId)
                .param("correlationId", correlationId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("decision correlation update failed");
        }
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
            Observation observation,
            String unit,
            String sourceId,
            String method,
            UUID evidenceId
    ) {
        jdbc.sql("""
                insert into intelligence.observation
                    (id, workspace_id, subject_type, subject_id, phenomenon,
                     value_json, source_type, quality_score, observed_at,
                     unit, source_id, method, evidence_id)
                values
                    (:id, :workspaceId, :subjectType, :subjectId, :phenomenon,
                     cast(:valueJson as jsonb), :sourceType, :qualityScore, :observedAt,
                     :unit, :sourceId, :method, :evidenceId)
                """)
                .param("id", observation.id())
                .param("workspaceId", observation.workspaceId())
                .param("subjectType", observation.subjectType())
                .param("subjectId", observation.subjectId())
                .param("phenomenon", observation.phenomenon())
                .param("valueJson", observation.valueJson())
                .param("sourceType", observation.sourceType())
                .param("qualityScore", observation.qualityScore())
                .param("observedAt", utc(observation.observedAt()))
                .param("unit", unit)
                .param("sourceId", sourceId)
                .param("method", method)
                .param("evidenceId", evidenceId)
                .update();
    }

    void enrichOutcome(
            UUID outcomeId,
            UUID observationId,
            Map<String, Object> varianceMetrics,
            double confidence,
            UUID correlationId,
            List<UUID> evidenceIds
    ) {
        int updated = jdbc.sql("""
                update intelligence.outcome
                   set observation_id = :observationId,
                       variance_json = cast(:variance as jsonb),
                       confidence = :confidence,
                       correlation_id = :correlationId
                 where id = :outcomeId
                """)
                .param("outcomeId", outcomeId)
                .param("observationId", observationId)
                .param("variance", json(varianceMetrics))
                .param("confidence", confidence)
                .param("correlationId", correlationId)
                .update();

        if (updated != 1) {
            throw new IllegalStateException("outcome enrichment failed");
        }

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

    String json(Object value) {
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

    private AlternativeRow alternativeRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AlternativeRow(
                rs.getObject("property_id", UUID.class),
                rs.getInt("rank"),
                rs.getDouble("life_fit_score"),
                rs.getDouble("confidence"),
                map(rs.getString("explanation")),
                (Integer) rs.getObject("expected_commute_minutes")
        );
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

record ModelVersionRow(
        UUID id,
        String modelId,
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
        String modelId,
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
