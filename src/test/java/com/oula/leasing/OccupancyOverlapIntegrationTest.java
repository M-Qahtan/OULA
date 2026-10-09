package com.oula.leasing;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OccupancyOverlapIntegrationTest {
    @Autowired LeasingService leasing;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseRejectsOverlappingOpenOccupancyAndRollsBackSecondActivation() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID evidenceOne = UUID.randomUUID();
        UUID evidenceTwo = UUID.randomUUID();
        seed(workspace, property, evidenceOne);
        seedEvidence(workspace, evidenceTwo);

        AccessContext access = new AccessContext(
                UUID.randomUUID(), "lease-manager", workspace,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(365, ChronoUnit.DAYS);

        Lease first = leasing.createDraft(
                access, property,
                command(UUID.randomUUID(), start, end, "5000.00"),
                UUID.randomUUID()
        );
        leasing.activate(access, first.id(), evidenceOne, "SA", null, UUID.randomUUID());

        Lease second = leasing.createDraft(
                access, property,
                command(UUID.randomUUID(), start.plus(30, ChronoUnit.DAYS),
                        end.plus(30, ChronoUnit.DAYS), "5200.00"),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> leasing.activate(
                access, second.id(), evidenceTwo, "SA", null, UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("overlapping open occupancy");

        String status = jdbc.queryForObject(
                "select status from leasing.lease where id=?",
                String.class, second.id()
        );
        assertThat(status).isEqualTo("DRAFT");

        Integer open = jdbc.queryForObject(
                "select count(*) from leasing.occupancy_period where property_id=? and status='OPEN'",
                Integer.class, property
        );
        assertThat(open).isEqualTo(1);
    }

    private CreateLeaseCommand command(
            UUID tenant,
            Instant start,
            Instant end,
            String rent
    ) {
        return new CreateLeaseCommand(
                UUID.randomUUID(), tenant, "RESIDENTIAL",
                start, end, new BigDecimal(rent), "SAR", "MONTHLY",
                null, null, "MANUAL_IMPORT"
        );
    }

    private void seed(UUID workspace, UUID property, UUID evidence) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Overlap Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
        seedEvidence(workspace, evidence);
    }

    private void seedEvidence(UUID workspace, UUID evidence) {
        jdbc.update("""
                insert into docs.evidence
                    (id, workspace_id, evidence_type, source, verification_status,
                     content_hash, captured_at)
                values (?,?,?,?,?,?,?)
                """,
                evidence, workspace, "DOCUMENT", "LEASE_CONTRACT",
                "VERIFIED", "sha256-test-" + evidence,
                java.sql.Timestamp.from(Instant.now())
        );
    }
}
