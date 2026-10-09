package com.oula.leasing;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LeasingIntegrationTest {
    @Autowired LeasingService leasing;
    @Autowired JdbcTemplate jdbc;

    @Test
    void activatesOnlyWithVerifiedEvidenceAndMaintainsCanonicalOccupancyLifecycle() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID landlord = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        seed(workspace, property, evidence, "UNVERIFIED");

        AccessContext access = new AccessContext(
                actor, "lease-manager", workspace, AccessPurpose.PROPERTY_MANAGEMENT
        );
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(364, ChronoUnit.DAYS);

        Lease draft = leasing.createDraft(
                access, property,
                new CreateLeaseCommand(
                        landlord, tenant, "RESIDENTIAL", startsAt, endsAt,
                        new BigDecimal("6000.00"), "SAR", "MONTHLY",
                        new BigDecimal("6000.00"), "ejar-ref-001", "MANUAL_IMPORT"
                ),
                UUID.randomUUID()
        );
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.contractEvidenceId()).isNull();

        assertThatThrownBy(() -> leasing.activate(
                access, draft.id(), evidence, "SA", null, UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("VERIFIED");

        jdbc.update(
                "update docs.evidence set verification_status='VERIFIED' where id=?",
                evidence
        );

        Lease active = leasing.activate(
                access, draft.id(), evidence, "SA", null, UUID.randomUUID()
        );
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.contractEvidenceId()).isEqualTo(evidence);

        OccupancyPeriod occupancy = leasing.currentOccupancy(access, property);
        assertThat(occupancy.leaseId()).isEqualTo(active.id());
        assertThat(occupancy.occupantPartyId()).isEqualTo(tenant);
        assertThat(occupancy.status()).isEqualTo("OPEN");

        Lease terminated = leasing.terminate(
                access, active.id(), "SA", null,
                "Mutual termination recorded by property manager",
                UUID.randomUUID()
        );
        assertThat(terminated.status()).isEqualTo("TERMINATED");

        assertThatThrownBy(() -> leasing.currentOccupancy(access, property))
                .isInstanceOf(NoSuchElementException.class);

        Integer closed = jdbc.queryForObject(
                "select count(*) from leasing.occupancy_period where lease_id=? and status='CLOSED'",
                Integer.class, active.id()
        );
        assertThat(closed).isEqualTo(1);
        assertThat(count("platform.audit_log")).isGreaterThanOrEqualTo(3);
        assertThat(count("platform.outbox_event")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void activatedLeaseTermsCannotBeRewritten() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        seed(workspace, property, evidence, "VERIFIED");

        AccessContext access = new AccessContext(
                UUID.randomUUID(), "lease-manager", workspace,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        Lease draft = leasing.createDraft(
                access, property,
                new CreateLeaseCommand(
                        UUID.randomUUID(), UUID.randomUUID(), "RESIDENTIAL",
                        Instant.now().minus(1, ChronoUnit.DAYS),
                        Instant.now().plus(30, ChronoUnit.DAYS),
                        new BigDecimal("4500.00"), "SAR", "MONTHLY",
                        null, null, "MANUAL_IMPORT"
                ),
                UUID.randomUUID()
        );
        Lease active = leasing.activate(
                access, draft.id(), evidence, "SA", null, UUID.randomUUID()
        );

        assertThatThrownBy(() -> jdbc.update(
                "update leasing.lease set rent_amount=9999 where id=?",
                active.id()
        )).hasMessageContaining("activated lease terms are immutable");
    }

    private void seed(
            UUID workspace,
            UUID property,
            UUID evidence,
            String verificationStatus
    ) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Leasing Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
        jdbc.update("""
                insert into docs.evidence
                    (id, workspace_id, evidence_type, source, verification_status,
                     content_hash, captured_at)
                values (?,?,?,?,?,?,?)
                """,
                evidence, workspace, "DOCUMENT", "LEASE_CONTRACT",
                verificationStatus, "sha256-test-" + evidence,
                java.sql.Timestamp.from(Instant.now())
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }
}
