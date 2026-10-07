package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DecisionIntelligenceApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper jsonMapper;

    @Test
    void closesPredictedDecidedActualLearningLoopWithTraceability() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID recommendedProperty = UUID.randomUUID();
        UUID alternativeProperty = UUID.randomUUID();

        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(recommendedProperty, workspace, "1800000");
        seedProperty(alternativeProperty, workspace, "1700000");
        seedSignal(intentId, recommendedProperty, 22);
        seedSignal(intentId, alternativeProperty, 35);
        seedVerifiedFact(recommendedProperty);
        seedDeclaredFact(alternativeProperty);

        UUID matchRunId = runMatching(actor, workspace, intentId);

        UUID decisionEvidenceId = registerEvidence(
                actor,
                workspace,
                "decision-evidence-" + UUID.randomUUID(),
                """
                {
                  "evidenceType": "INSPECTION",
                  "sourceType": "PROPERTY_INSPECTION",
                  "sourceIdentity": "inspection-2026-001",
                  "contentHash": "sha256:inspection-evidence",
                  "verificationStatus": "VERIFIED",
                  "jurisdiction": "SA"
                }
                """
        );

        UUID assumptionId = registerAssumption(
                actor,
                workspace,
                "assumption-" + UUID.randomUUID()
        );

        UUID recommendationId = generateRecommendation(
                actor,
                workspace,
                matchRunId,
                decisionEvidenceId,
                assumptionId,
                recommendedProperty
        );

        mvc.perform(get(
                        "/v1/intelligence/recommendations/{recommendationId}/explain",
                        recommendationId
                )
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.read"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("LifeFit"))
                .andExpect(jsonPath("$.modelVersion").value("v1"))
                .andExpect(jsonPath("$.modelType").value("RULE_ENGINE"))
                .andExpect(jsonPath("$.riskClass").value("R2"))
                .andExpect(jsonPath("$.recommendedPropertyId").value(recommendedProperty.toString()))
                .andExpect(jsonPath("$.evidenceIds[0]").value(decisionEvidenceId.toString()))
                .andExpect(jsonPath("$.assumptionIds[0]").value(assumptionId.toString()))
                .andExpect(jsonPath("$.alternatives.length()").value(2))
                .andExpect(jsonPath("$.uncertainty.knownLimitation").exists());

        UUID decisionId = recordDecision(
                actor,
                workspace,
                recommendationId,
                recommendedProperty
        );

        UUID outcomeEvidenceId = registerEvidence(
                actor,
                workspace,
                "outcome-evidence-" + UUID.randomUUID(),
                """
                {
                  "evidenceType": "USER_REPORTED_OUTCOME",
                  "sourceType": "POST_DECISION_FEEDBACK",
                  "sourceIdentity": "household-feedback-session",
                  "contentHash": "sha256:outcome-evidence",
                  "verificationStatus": "UNVERIFIED",
                  "jurisdiction": "SA"
                }
                """
        );

        String outcomeKey = "outcome-" + UUID.randomUUID();
        String outcomeBody = """
                {
                  "actualCommuteMinutes": 27,
                  "satisfactionScore": 88,
                  "confidence": 0.90,
                  "evidenceIds": ["%s"]
                }
                """.formatted(outcomeEvidenceId);

        MvcResult outcome = mvc.perform(post(
                        "/v1/intelligence/decisions/{decisionId}/outcomes",
                        decisionId
                )
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.outcome.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", outcomeKey)
                        .contentType("application/json")
                        .content(outcomeBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.expectedMetrics.commuteMinutes").value(22))
                .andExpect(jsonPath("$.actualMetrics.commuteMinutes").value(27))
                .andExpect(jsonPath("$.actualMetrics.satisfactionScore").value(88.0))
                .andExpect(jsonPath("$.varianceMetrics.commuteMinutes").value(5))
                .andReturn();

        UUID outcomeId = uuid(outcome, "outcomeId");

        mvc.perform(post(
                        "/v1/intelligence/decisions/{decisionId}/outcomes",
                        decisionId
                )
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.outcome.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", outcomeKey)
                        .contentType("application/json")
                        .content(outcomeBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.outcomeId").value(outcomeId.toString()));

        assertThat(count("intelligence.evidence")).isEqualTo(2);
        assertThat(count("intelligence.assumption")).isEqualTo(1);
        assertThat(count("intelligence.recommendation")).isEqualTo(1);
        assertThat(count("intelligence.decision_record")).isEqualTo(1);
        assertThat(count("intelligence.observation")).isEqualTo(1);
        assertThat(count("intelligence.outcome")).isEqualTo(1);
        assertThat(count("platform.audit_log")).isEqualTo(6);
        assertThat(count("platform.outbox_event")).isEqualTo(7);
    }

    @Test
    void humanOverrideRequiresReasonAndIsNeverSilentlyConvertedToAcceptance() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID recommendedProperty = UUID.randomUUID();
        UUID alternativeProperty = UUID.randomUUID();

        seedWorkspace(workspace);
        seedIntent(intentId, workspace);
        seedProperty(recommendedProperty, workspace, "1800000");
        seedProperty(alternativeProperty, workspace, "1700000");
        seedSignal(intentId, recommendedProperty, 22);
        seedSignal(intentId, alternativeProperty, 35);
        seedVerifiedFact(recommendedProperty);
        seedDeclaredFact(alternativeProperty);

        UUID matchRunId = runMatching(actor, workspace, intentId);
        UUID evidenceId = registerEvidence(
                actor,
                workspace,
                "evidence-" + UUID.randomUUID(),
                """
                {
                  "evidenceType": "INSPECTION",
                  "sourceType": "PROPERTY_INSPECTION",
                  "sourceIdentity": "inspection-override-case",
                  "contentHash": "sha256:override-evidence",
                  "verificationStatus": "VERIFIED"
                }
                """
        );
        UUID recommendationId = generateRecommendation(
                actor,
                workspace,
                matchRunId,
                evidenceId,
                null,
                recommendedProperty
        );

        mvc.perform(post(
                        "/v1/intelligence/recommendations/{recommendationId}/decisions",
                        recommendationId
                )
                        .with(token(actor, workspace, "oula.intelligence.decide"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "decision-no-reason-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedPropertyId": "%s"
                                }
                                """.formatted(alternativeProperty)))
                .andExpect(status().isBadRequest());

        mvc.perform(post(
                        "/v1/intelligence/recommendations/{recommendationId}/decisions",
                        recommendationId
                )
                        .with(token(actor, workspace, "oula.intelligence.decide"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "decision-with-reason-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedPropertyId": "%s",
                                  "overrideReason": "The household prioritizes a non-modeled family constraint."
                                }
                                """.formatted(alternativeProperty)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedRecommendation").value(false))
                .andExpect(jsonPath("$.selectedPropertyId").value(alternativeProperty.toString()));

        assertThat(count("intelligence.decision_record")).isEqualTo(1);
    }

    private UUID runMatching(UUID actor, UUID workspace, UUID intentId) throws Exception {
        MvcResult result = mvc.perform(post("/v1/intents/{intentId}/matches", intentId)
                        .with(token(actor, workspace, "oula.matching.run"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "match-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andReturn();

        return uuid(result, "matchRunId");
    }

    private UUID registerEvidence(
            UUID actor,
            UUID workspace,
            String idempotencyKey,
            String body
    ) throws Exception {
        MvcResult result = mvc.perform(post("/v1/intelligence/evidence")
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.evidence.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return uuid(result, "id");
    }

    private UUID registerAssumption(
            UUID actor,
            UUID workspace,
            String idempotencyKey
    ) throws Exception {
        MvcResult result = mvc.perform(post("/v1/intelligence/assumptions")
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.assumption.write"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "statement": "Weekday peak commute remains representative during the decision horizon.",
                                  "value": {"period": "weekday_peak"},
                                  "source": "household decision interview",
                                  "reason": "LifeFit mobility dimension depends on a representative commute assumption.",
                                  "confidence": 0.75,
                                  "sensitivity": "MEDIUM"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        return uuid(result, "id");
    }

    private UUID generateRecommendation(
            UUID actor,
            UUID workspace,
            UUID matchRunId,
            UUID evidenceId,
            UUID assumptionId,
            UUID expectedProperty
    ) throws Exception {
        String assumptions = assumptionId == null
                ? "[]"
                : "[\"" + assumptionId + "\"]";

        MvcResult result = mvc.perform(post("/v1/intelligence/recommendations")
                        .with(token(
                                actor,
                                workspace,
                                "oula.intelligence.recommend"
                        ))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "recommendation-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "matchRunId": "%s",
                                  "evidenceIds": ["%s"],
                                  "assumptionIds": %s,
                                  "validHours": 72
                                }
                                """.formatted(matchRunId, evidenceId, assumptions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendedPropertyId").value(expectedProperty.toString()))
                .andExpect(jsonPath("$.modelId").value("LifeFit"))
                .andExpect(jsonPath("$.modelVersion").value("v1"))
                .andReturn();

        return uuid(result, "recommendationId");
    }

    private UUID recordDecision(
            UUID actor,
            UUID workspace,
            UUID recommendationId,
            UUID selectedProperty
    ) throws Exception {
        MvcResult result = mvc.perform(post(
                        "/v1/intelligence/recommendations/{recommendationId}/decisions",
                        recommendationId
                )
                        .with(token(actor, workspace, "oula.intelligence.decide"))
                        .header("X-OULA-Workspace-ID", workspace)
                        .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT")
                        .header("Idempotency-Key", "decision-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedPropertyId": "%s"
                                }
                                """.formatted(selectedProperty)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedRecommendation").value(true))
                .andReturn();

        return uuid(result, "decisionId");
    }

    private RequestPostProcessor token(
            UUID actor,
            UUID workspace,
            String... scopes
    ) {
        List<GrantedAuthority> authorities = Arrays.stream(scopes)
                .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        return jwt()
                .jwt(jwt -> jwt
                        .subject("subject-" + actor)
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(workspace.toString()))
                        .claim("oula_purposes", List.of("PROPERTY_DECISION_SUPPORT")))
                .authorities(authorities);
    }

    private UUID uuid(MvcResult result, String field) throws Exception {
        JsonNode json = jsonMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get(field).asText());
    }

    private void seedWorkspace(UUID workspace) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Decision Intelligence Test', 'ACTIVE')",
                workspace
        );
    }

    private void seedIntent(UUID intentId, UUID workspace) {
        jdbc.update("""
                insert into intent.intent
                    (id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values
                    (?, ?, 'BUY', 'ACTIVE', 2000000, 4, '["Al Yasmin"]'::jsonb)
                """, intentId, workspace);
    }

    private void seedProperty(UUID propertyId, UUID workspace, String price) {
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values
                    (?, ?, 'RESIDENTIAL', 'Al Yasmin', 4, ?::numeric)
                """, propertyId, workspace, price);
    }

    private void seedSignal(UUID intentId, UUID propertyId, int commuteMinutes) {
        jdbc.update("""
                insert into matching.intent_property_signal
                    (intent_id, property_id, commute_minutes, source_type, confidence)
                values
                    (?, ?, ?, 'ROUTING_ENGINE', 0.95)
                """, intentId, propertyId, commuteMinutes);
    }

    private void seedVerifiedFact(UUID propertyId) {
        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values
                    (?, ?, 'bedrooms_verified', '{"value":4}'::jsonb, 'VERIFIED', 'INSPECTION', 1.0)
                """, UUID.randomUUID(), propertyId);
    }

    private void seedDeclaredFact(UUID propertyId) {
        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values
                    (?, ?, 'bedrooms_declared', '{"value":4}'::jsonb, 'DECLARED', 'OWNER', 0.7)
                """, UUID.randomUUID(), propertyId);
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }
}
