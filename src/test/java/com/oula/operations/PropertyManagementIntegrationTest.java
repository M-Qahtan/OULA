package com.oula.operations;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PropertyManagementIntegrationTest {
    @Autowired PropertyManagementService management;
    @Autowired JdbcTemplate jdbc;

    @Test
    void turnsAnObligationIntoGuardianSignalActionAndResolvedLifecycle() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        AccessContext access = new AccessContext(
                actorId,
                "management-test",
                workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );

        ManagementEnrollment enrollment = management.enroll(
                access, propertyId, UUID.randomUUID()
        );
        assertThat(enrollment.status()).isEqualTo("ACTIVE");

        Obligation obligation = management.createObligation(
                access,
                propertyId,
                new CreateObligationCommand(
                        "LEASE_RENEWAL",
                        "Renew tenant lease",
                        Instant.now().plusSeconds(24 * 3600),
                        "HIGH",
                        "CONTRACT",
                        "lease-001"
                ),
                UUID.randomUUID()
        );
        assertThat(obligation.status()).isEqualTo("OPEN");

        GuardianAssessment first = management.assess(
                access, propertyId, UUID.randomUUID()
        );
        assertThat(first.obligationsEvaluated()).isEqualTo(1);
        assertThat(first.signalsCreated()).isEqualTo(1);
        assertThat(first.actionsCreated()).isEqualTo(1);

        GuardianAssessment second = management.assess(
                access, propertyId, UUID.randomUUID()
        );
        assertThat(second.signalsCreated()).isZero();
        assertThat(second.actionsCreated()).isZero();

        ManagementOverview before = management.overview(access, propertyId);
        assertThat(before.obligations()).hasSize(1);
        assertThat(before.guardianSignals()).hasSize(1);
        assertThat(before.actions()).hasSize(1);
        assertThat(before.guardianSignals().getFirst().severity()).isEqualTo("HIGH");
        assertThat(before.actions().getFirst().status()).isEqualTo("OPEN");

        ActionItem completed = management.completeAction(
                access,
                before.actions().getFirst().id(),
                "Lease renewed and verified",
                UUID.randomUUID()
        );
        assertThat(completed.status()).isEqualTo("COMPLETED");

        ManagementOverview after = management.overview(access, propertyId);
        assertThat(after.obligations().getFirst().status()).isEqualTo("SATISFIED");
        assertThat(after.guardianSignals().getFirst().status()).isEqualTo("RESOLVED");
        assertThat(after.actions().getFirst().status()).isEqualTo("COMPLETED");

        assertThat(count("ops.management_enrollment")).isEqualTo(1);
        assertThat(count("ops.obligation")).isEqualTo(1);
        assertThat(count("ops.guardian_signal")).isEqualTo(1);
        assertThat(count("ops.action_item")).isEqualTo(1);
        assertThat(count("platform.outbox_event")).isGreaterThanOrEqualTo(4);
        assertThat(count("platform.audit_log")).isGreaterThanOrEqualTo(4);
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Management Test", "ACTIVE"
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
