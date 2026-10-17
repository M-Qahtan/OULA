package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.intent.IntentStatus;
import com.oula.property.PropertyCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CI_DB_TEST", matches = "true")
class MatchRunPersistenceIntegrationTest {

    @Autowired MatchRunApplicationService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsRankedResultsForEligibleCandidates() {
        UUID workspace = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID propertyA = UUID.randomUUID();
        UUID propertyB = UUID.randomUUID();

        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Test', 'ACTIVE')", workspace);
        jdbc.update("""
                insert into intent.intent(id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values (?, ?, 'BUY', 'ACTIVE', 2000000, 4, '["Al Yasmin"]'::jsonb)
                """, intentId, workspace);
        jdbc.update("""
                insert into property.asset(id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?, ?, 'RESIDENTIAL', 'Al Yasmin', 4, 1800000)
                """, propertyA, workspace);
        jdbc.update("""
                insert into property.asset(id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?, ?, 'RESIDENTIAL', 'Al Yasmin', 3, 1500000)
                """, propertyB, workspace);

        Intent intent = new Intent(intentId, workspace, new BigDecimal("2000000"), 4, Set.of("Al Yasmin"), IntentStatus.ACTIVE);
        List<PropertyCandidate> supply = List.of(
                new PropertyCandidate(propertyA, workspace, new BigDecimal("1800000"), 4, "Al Yasmin", 22, 18, 20),
                new PropertyCandidate(propertyB, workspace, new BigDecimal("1500000"), 3, "Al Yasmin", 18, 20, 20)
        );

        MatchRunOutcome outcome = service.run(intent, supply, UUID.randomUUID());

        assertThat(outcome.matches()).hasSize(1);
        assertThat(outcome.matches().getFirst().propertyId()).isEqualTo(propertyA);

        Integer persisted = jdbc.queryForObject(
                "select count(*) from matching.match_result where match_run_id = ?",
                Integer.class,
                outcome.matchRunId()
        );
        String status = jdbc.queryForObject(
                "select status from matching.match_run where id = ?",
                String.class,
                outcome.matchRunId()
        );

        assertThat(persisted).isEqualTo(1);
        assertThat(status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select algorithm_version from matching.match_run where id=?",
                String.class, outcome.matchRunId())).isEqualTo(LifeFitCalculator.ALGORITHM_VERSION);
        assertThat(jdbc.queryForObject("select payload->>'algorithmVersion' from platform.outbox_event where aggregate_id=?",
                String.class, outcome.matchRunId())).isEqualTo(LifeFitCalculator.ALGORITHM_VERSION);
        assertThat(jdbc.queryForObject("select version from intelligence.model_version where runtime_key=? and status='ACTIVE'",
                String.class, LifeFitCalculator.ALGORITHM_VERSION)).isEqualTo("v2-decimal");
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("default-locale")
    void persistsNumericExplanationWithCommaDecimalHostLocale() {
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
            persistsRankedResultsForEligibleCandidates();
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }
}
