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

@SpringBootTest
@Transactional
class PropertyPassportIntegrationTest {
    @Autowired PropertyPassportService passports;
    @Autowired PropertyStateSnapshotService snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void composesCanonicalFactsAndLatestObservationWithoutCollapsingTruthStatuses() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        UUID verifiedFact = UUID.randomUUID();
        UUID declaredFact = UUID.randomUUID();
        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values (?,?,'bedrooms_verified','{"value":4}'::jsonb,'VERIFIED','INSPECTION',1.0)
                """, verifiedFact, propertyId);
        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status, source_type, confidence)
                values (?,?,'renovation_note','{"value":"kitchen upgraded"}'::jsonb,'DECLARED','OWNER',0.7)
                """, declaredFact, propertyId);

        AccessContext access = new AccessContext(
                actorId,
                "passport-test",
                workspaceId,
                AccessPurpose.PROPERTY_DECISION_SUPPORT
        );
        PropertyStateSnapshot state = snapshots.record(
                access,
                propertyId,
                new RecordPropertyStateSnapshotCommand(
                        Instant.now().minusSeconds(60),
                        "OBSERVATIONS",
                        Map.of("occupancy", "OCCUPIED", "observedCondition", "GOOD"),
                        "INSPECTION",
                        "inspection-passport-1",
                        List.of(UUID.randomUUID())
                ),
                UUID.randomUUID()
        );

        PropertyPassport passport = passports.get(access, propertyId);

        assertThat(passport.propertyId()).isEqualTo(propertyId);
        assertThat(passport.assetType()).isEqualTo("RESIDENTIAL");
        assertThat(passport.facts()).hasSize(2);
        assertThat(passport.facts())
                .extracting(PropertyPassportFact::truthStatus)
                .containsExactlyInAnyOrder("VERIFIED", "DECLARED");
        assertThat(passport.verifiedFactCoverage()).isEqualTo(0.5);
        assertThat(passport.latestState().snapshotId()).isEqualTo(state.snapshotId());
        assertThat(passport.latestState().stateBasis()).isEqualTo("OBSERVATIONS");
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Passport Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
    }
}
