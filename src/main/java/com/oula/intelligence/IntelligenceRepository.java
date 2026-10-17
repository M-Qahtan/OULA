package com.oula.intelligence;

import com.oula.matching.MatchAlternativeDecisionView;
import com.oula.platform.RequestFingerprint;
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

    /**
     * Capture the real persisted recommendation inputs within the same transaction
     * as admission and recommendation creation. The exact serialized bytes and
     * a versioned SHA-256 digest remain stable when live evidence changes later.
     * This is inspectable provenance, not calibrated prediction reproducibility.
     */
    void saveRecommendationInputSnapshot(UUID recommendationId, UUID workspaceId,
                                          String algorithmVersion) {
        String payload=jdbc.sql("""
                select jsonb_build_object(
                    'schema','OULA_RECOMMENDATION_INPUT_V1',
                    'recommendationId',r.id,
                    'workspaceId',r.workspace_id,
                    'matchRunId',r.match_run_id,
                    'algorithmVersion',:algorithm,
                    'modelVersionId',r.model_version_id,
                    'recommendedPropertyId',r.recommended_property_id,
                    'lifeFitScore',r.life_fit_score,
                    'confidenceBreakdown',r.confidence_breakdown,
                    'uncertainty',r.uncertainty,
                    'evidence',coalesce((
                        select jsonb_agg(jsonb_build_object(
                          'id',e.id, 'subjectType',e.subject_type, 'subjectId',e.subject_id,
                          'evidenceType',e.evidence_type, 'sourceType',e.source_type,
                          'sourceIdentity',e.source_identity, 'contentHash',e.content_hash,
                          'authorityId',t.authority_id, 'attestedHash',t.attested_hash,
                          'attestedAt',t.attested_at
                        ) order by e.id)
                        from intelligence.recommendation_evidence re
                        join intelligence.evidence e on e.id=re.evidence_id
                        join intelligence.evidence_attestation t on t.evidence_id=e.id
                        where re.recommendation_id=r.id
                    ),'[]'::jsonb),
                    'assumptions',coalesce((
                        select jsonb_agg(jsonb_build_object(
                          'id',a.id,'statement',a.statement,'value',a.value_json,
                          'confidence',a.confidence,'sensitivity',a.sensitivity,
                          'validUntil',a.valid_until
                        ) order by a.id)
                        from intelligence.recommendation_assumption ra
                        join intelligence.assumption a on a.id=ra.assumption_id
                        where ra.recommendation_id=r.id
                    ),'[]'::jsonb),
                    'rankedAlternatives',coalesce((
                        select jsonb_agg(jsonb_build_object(
                          'propertyId',alt.property_id,'rank',alt.rank,
                          'lifeFitScore',alt.life_fit_score,'confidence',alt.confidence,
                          'explanation',alt.explanation
                        ) order by alt.rank,alt.property_id)
                        from intelligence.recommendation_alternative alt
                        where alt.recommendation_id=r.id
                    ),'[]'::jsonb)
                )::text
                from intelligence.recommendation r
                where r.id=:id and r.workspace_id=:ws
                """).param("id",recommendationId).param("ws",workspaceId)
                .param("algorithm",algorithmVersion).query(String.class).optional()
                .orElseThrow(()->new IllegalStateException("snapshot source recommendation missing"));
        String hash=RequestFingerprint.sha256("OULA_RECOMMENDATION_INPUT_V1",payload);
        int created=jdbc.sql("""
                insert into intelligence.recommendation_input_snapshot
                   (recommendation_id,workspace_id,schema_version,payload_json,payload_hash)
                values (:id,:ws,'OULA_RECOMMENDATION_INPUT_V1',:payload,:hash)
                """).param("id",recommendationId).param("ws",workspaceId)
                .param("payload",payload).param("hash",hash).update();
        if(created!=1)throw new IllegalStateException("snapshot insertion failed");
    }

    /**
     * Shared lock serializes any concurrent payload/hash mutation with admission.
     * Must be called inside the surrounding service transaction (including GET).
     */
    java.util.Optional<InputSnapshotRow> lockedInputSnapshot(
            UUID recommendationId, UUID workspaceId) {
        return jdbc.sql("""
                select schema_version,payload_json,payload_hash
                  from intelligence.recommendation_input_snapshot
                 where recommendation_id=:id and workspace_id=:ws
                 for share
                """)
                .param("id",recommendationId).param("ws",workspaceId)
                .query((rs,n)->new InputSnapshotRow(
                        rs.getString("schema_version"),
                        rs.getString("payload_json"),
                        rs.getString("payload_hash").trim()))
                .optional();
    }

    record InputSnapshotRow(String schemaVersion, String payload, String storedHash) {}

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
                       r.intent_id,
                       r.model_version_id
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
                        rs.getObject("intent_id", UUID.class),
                        rs.getObject("model_version_id", UUID.class)
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
        UUID intentId,
        UUID modelVersionId
) {
}
