package com.oula.property;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PropertyStateSnapshotIntegrationTest {
    @Autowired PropertyStateSnapshotService snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsImmutableVersionLineageAndPreventsHindsightInAsOfReads() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        AccessContext access = new AccessContext(
                actorId,
                "snapshot-test",
                workspaceId,
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );

        PropertyStateSnapshot first = snapshots.record(
                access,
                propertyId,
                new RecordPropertyStateSnapshotCommand(
                        Instant.now().minusSeconds(3600),
                        "OBSERVATIONS",
                        Map.of("occupancy", "VACANT"),
                        "INSPECTION",
                        "inspection-1",
                        List.of(UUID.randomUUID())
                ),
                UUID.randomUUID()
        );

        PropertyStateSnapshot second = snapshots.record(
                access,
                propertyId,
                new RecordPropertyStateSnapshotCommand(
                        Instant.now().minusSeconds(60),
                        "OBSERVATIONS",
                        Map.of("occupancy", "OCCUPIED"),
                        "INSPECTION",
                        "inspection-2",
                        List.of(UUID.randomUUID())
                ),
                UUID.randomUUID()
        );

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.supersedesSnapshotId()).isEqualTo(first.snapshotId());
        assertThat(jdbc.queryForObject(
                "select count(*) from property.state_snapshot where property_id = ?",
                Long.class,
                propertyId
        )).isEqualTo(2L);

        PropertyStateSnapshot knownAtFirstRecord = snapshots.latestKnownAt(
                access,
                propertyId,
                first.recordedAt()
        );
        assertThat(knownAtFirstRecord.snapshotId()).isEqualTo(first.snapshotId());
    }

    @Test
    void rejectsObservationSnapshotWithoutEvidenceReference() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        AccessContext access = new AccessContext(
                actorId,
                "snapshot-evidence-test",
                workspaceId,
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );

        assertThatThrownBy(() -> snapshots.record(
                access,
                propertyId,
                new RecordPropertyStateSnapshotCommand(
                        Instant.now(),
                        "OBSERVATIONS",
                        Map.of("occupancy", "VACANT"),
                        "INSPECTION",
                        "inspection-without-evidence",
                        List.of()
                ),
                UUID.randomUUID()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires evidence references");
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Snapshot Test", "ACTIVE"
        );
        jdbc.update(
                """
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Riyadh", 4, 2_000_000
        );
    }
}
