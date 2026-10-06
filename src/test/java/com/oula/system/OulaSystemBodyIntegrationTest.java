package com.oula.system;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.orchestration.OulaJourneyOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CI_DB_TEST", matches = "true")
class OulaSystemBodyIntegrationTest {
    @Autowired OulaJourneyOrchestrator orchestrator;
    @Autowired JdbcTemplate jdbc;

    @Test
    void connectsIamIntentPropertyMatchingAndIntelligenceAsOneBody() {
        UUID workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID intent = UUID.randomUUID();
        UUID propertyA = UUID.randomUUID();
        UUID propertyB = UUID.randomUUID();

        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Body Test','ACTIVE')", workspace);
        jdbc.update("""
                insert into intent.intent(id,workspace_id,intent_type,status,budget_max,minimum_bedrooms,preferred_districts)
                values (?,?,'BUY','ACTIVE',2000000,4,'["Al Yasmin"]'::jsonb)
                """, intent, workspace);
        jdbc.update("insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price) values (?,?,'RESIDENTIAL','Al Yasmin',4,1850000)", propertyA, workspace);
        jdbc.update("insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price) values (?,?,'RESIDENTIAL','Al Yasmin',4,1950000)", propertyB, workspace);
        jdbc.update("insert into matching.intent_property_signal(intent_id,property_id,commute_minutes,source_type,confidence) values (?,?,20,'TEST',1)", intent, propertyA);
        jdbc.update("insert into matching.intent_property_signal(intent_id,property_id,commute_minutes,source_type,confidence) values (?,?,28,'TEST',1)", intent, propertyB);
        jdbc.update("insert into property.fact(id,property_id,fact_key,value_json,truth_status,source_type,confidence) values (?,?, 'area','185'::jsonb,'VERIFIED','TEST',1)", UUID.randomUUID(), propertyA);
        jdbc.update("insert into property.fact(id,property_id,fact_key,value_json,truth_status,source_type,confidence) values (?,?, 'area','195'::jsonb,'VERIFIED','TEST',1)", UUID.randomUUID(), propertyB);

        AccessContext access = new AccessContext(actor, "test-subject", workspace, AccessPurpose.PROPERTY_DECISION_SUPPORT);
        var result = orchestrator.recommend(access, intent, UUID.randomUUID());

        assertThat(result.matches().matches()).hasSize(2);
        assertThat(result.recommendation().recommendedPropertyId()).isEqualTo(propertyA);
        assertThat(jdbc.queryForObject("select count(*) from intelligence.recommendation where intent_id=?", Integer.class, intent)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform.outbox_event where workspace_id=?", Integer.class, workspace)).isGreaterThanOrEqualTo(1);
    }
}
