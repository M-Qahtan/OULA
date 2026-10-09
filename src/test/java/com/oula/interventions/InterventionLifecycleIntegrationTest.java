package com.oula.interventions;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.*;
import com.oula.vitals.PropertyVitalsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InterventionLifecycleIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PropertyManagementService management;
    @Autowired PropertyVitalsService vitals;
    @Autowired InterventionService interventions;

    @Test
    void humanAcknowledgesRealAdvisoryThenRecordsNoncausalObservedImprovement() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seed(workspace, property);
        AccessContext access = new AccessContext(
                actor, "intervention-test", workspace, AccessPurpose.PROPERTY_MANAGEMENT
        );

        management.enroll(access, property, UUID.randomUUID());
        management.createObligation(access, property,
                new CreateObligationCommand(
                        "PROPERTY_MAINTENANCE", "Investigate overdue safety inspection",
                        Instant.now().minusSeconds(7200),
                        "HIGH", "MAINTENANCE_PLAN", "inspection-18"
                ), UUID.randomUUID());
        management.assess(access, property, UUID.randomUUID());
        var before = vitals.assess(access, property, UUID.randomUUID());
        assertThat(before.obligationStatus().name()).isEqualTo("RED");

        InterventionReview review = interventions.review(
                access, property, new RecordInterventionReviewCommand(
                        before.id(), "OBLIGATIONS", "REVIEW_OBLIGATIONS",
                        "ACKNOWLEDGED", "Human reviewed the overdue obligation, not approval of payment"),
                UUID.randomUUID());
        assertThat(review.sourceSnapshotId()).isEqualTo(before.id());
        assertThat(review.decision()).isEqualTo("ACKNOWLEDGED");

        ActionItem action = management.overview(access, property).actions().getFirst();
        management.completeAction(access, action.id(),
                "Inspection follow-up recorded by manager", UUID.randomUUID());
        var after = vitals.assess(access, property, UUID.randomUUID());
        assertThat(after.obligationStatus().name()).isEqualTo("GREEN");

        InterventionOutcome outcome = interventions.observe(
                access, review.id(), new RecordInterventionOutcomeCommand(
                        after.id(), null, "Later data shows obligations closed; cause not established"),
                UUID.randomUUID());
        assertThat(outcome.beforeStatus().name()).isEqualTo("RED");
        assertThat(outcome.afterStatus().name()).isEqualTo("GREEN");
        assertThat(outcome.observedDirection()).isEqualTo("IMPROVED");
        assertThat(outcome.executionEvidenceLevel()).isEqualTo("OBSERVATION_ONLY");
        assertThat(outcome.workOrderId()).isNull();

        InterventionHistory history = interventions.history(access, property);
        assertThat(history.reviews()).hasSize(1);
        assertThat(history.outcomes()).hasSize(1);
        assertThat(count("interventions.review")).isEqualTo(1);
        assertThat(count("interventions.outcome_observation")).isEqualTo(1);
        assertThat(count("ops.work_order")).isZero();
        assertThat(count("platform.audit_log")).isGreaterThanOrEqualTo(2);
        assertThat(count("platform.outbox_event")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rejectsForeignPurposeAndUnproposedActionWithoutNewRecords() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seed(workspace, property);
        AccessContext access = new AccessContext(
                actor, "intervention-test", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        management.enroll(access, property, UUID.randomUUID());
        var source = vitals.assess(access, property, UUID.randomUUID());

        assertThatThrownBy(() -> interventions.review(
                access, property, new RecordInterventionReviewCommand(
                        source.id(), "OBLIGATIONS", "PAY_OUT_MONEY",
                        "ACKNOWLEDGED", "Try to invent an action"),
                UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not recommended");

        AccessContext denied = new AccessContext(actor, "not-human", workspace,
                AccessPurpose.AUTONOMOUS_EXECUTION);
        assertThatThrownBy(() -> interventions.review(
                denied, property, new RecordInterventionReviewCommand(
                        source.id(), "OBLIGATIONS", "REVIEW_OBLIGATIONS",
                        "ACKNOWLEDGED", "Automated agent cannot attest human review"),
                UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        assertThat(count("interventions.review")).isZero();
    }

    private void seed(UUID workspace, UUID property) {
        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Intervention Test", "ACTIVE");
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """, property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000);
    }

    private long count(String table) {
        Long result = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return result == null ? 0 : result;
    }
}
