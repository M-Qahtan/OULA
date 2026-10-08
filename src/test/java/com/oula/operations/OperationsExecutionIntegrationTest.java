package com.oula.operations;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OperationsExecutionIntegrationTest {
    @Autowired PropertyManagementService management;
    @Autowired OperationsExecutionService execution;
    @Autowired JdbcTemplate jdbc;

    @Test
    void executesGuardianActionThroughApprovedEvidenceBackedWorkOrder() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        AccessContext access = new AccessContext(
                actorId, "operations-test", workspaceId, AccessPurpose.PROPERTY_MANAGEMENT
        );

        management.enroll(access, propertyId, UUID.randomUUID());
        management.createObligation(
                access,
                propertyId,
                new CreateObligationCommand(
                        "HVAC_MAINTENANCE", "Service HVAC", Instant.now().plusSeconds(3600),
                        "HIGH", "MAINTENANCE_PLAN", "plan-001"
                ),
                UUID.randomUUID()
        );
        management.assess(access, propertyId, UUID.randomUUID());
        ActionItem action = management.overview(access, propertyId).actions().getFirst();

        WorkOrder created = execution.createFromAction(
                access,
                action.id(),
                new CreateWorkOrderCommand(
                        "HVAC", "Annual HVAC service", "Inspect, clean and test cooling equipment",
                        new BigDecimal("4200.00"), "SAR"
                ),
                UUID.randomUUID()
        );
        assertThat(created.status()).isEqualTo("PENDING_APPROVAL");

        WorkOrder approved = execution.approve(
                access, created.id(), new BigDecimal("5000.00"), UUID.randomUUID()
        );
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvalActorId()).isEqualTo(actorId);

        WorkOrder assigned = execution.assign(
                access, created.id(), providerId, UUID.randomUUID()
        );
        assertThat(assigned.status()).isEqualTo("ASSIGNED");

        WorkOrder started = execution.start(access, created.id(), UUID.randomUUID());
        assertThat(started.status()).isEqualTo("IN_PROGRESS");

        assertThatThrownBy(() -> execution.submitCompletion(
                access,
                created.id(),
                new SubmitWorkOrderCompletionCommand(
                        new BigDecimal("5100.00"), evidenceId, "Completed with extra work"
                ),
                UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("exceeds approved budget");

        WorkOrder submitted = execution.submitCompletion(
                access,
                created.id(),
                new SubmitWorkOrderCompletionCommand(
                        new BigDecimal("4700.00"), evidenceId,
                        "HVAC serviced, tested and completion evidence attached"
                ),
                UUID.randomUUID()
        );
        assertThat(submitted.status()).isEqualTo("COMPLETION_REVIEW");
        assertThat(submitted.completionEvidenceId()).isEqualTo(evidenceId);

        WorkOrder completed = execution.verifyCompletion(
                access, created.id(), UUID.randomUUID()
        );
        assertThat(completed.status()).isEqualTo("COMPLETED");

        ManagementOverview overview = management.overview(access, propertyId);
        assertThat(overview.actions().getFirst().status()).isEqualTo("COMPLETED");
        assertThat(overview.obligations().getFirst().status()).isEqualTo("SATISFIED");
        assertThat(overview.guardianSignals().getFirst().status()).isEqualTo("RESOLVED");

        assertThat(count("ops.work_order")).isEqualTo(1);
        assertThat(count("platform.outbox_event")).isGreaterThanOrEqualTo(10);
        assertThat(count("platform.audit_log")).isGreaterThanOrEqualTo(10);
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Execution Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }
}
