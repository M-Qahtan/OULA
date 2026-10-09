package com.oula.vitals;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PropertyVitalsIntegrationTest {
    @Autowired PropertyVitalsService vitals;
    @Autowired JdbcTemplate jdbc;

    @Test
    void derivesTransparentVitalStatesFromCanonicalOperationalTruth() {
        Fixture f = seedOperationalHistory();
        AccessContext access = new AccessContext(
                f.actorId(), "vitals-test", f.workspaceId(), AccessPurpose.PROPERTY_MANAGEMENT
        );

        PropertyVitalSnapshot snapshot = vitals.assess(
                access, f.propertyId(), UUID.randomUUID()
        );

        assertThat(snapshot.overallStatus()).isEqualTo(VitalStatus.RED);
        assertThat(snapshot.obligationStatus()).isEqualTo(VitalStatus.RED);
        assertThat(snapshot.guardianStatus()).isEqualTo(VitalStatus.RED);
        assertThat(snapshot.executionStatus()).isEqualTo(VitalStatus.GREEN);
        assertThat(snapshot.costStatus()).isEqualTo(VitalStatus.AMBER);
        assertThat(snapshot.providerStatus()).isEqualTo(VitalStatus.RED);
        assertThat(snapshot.evidenceStatus()).isEqualTo(VitalStatus.GREEN);
        assertThat(snapshot.truthStatus()).isEqualTo(VitalStatus.AMBER);
        assertThat(snapshot.freshnessStatus()).isEqualTo(VitalStatus.GREEN);
        assertThat(snapshot.knownDimensionCount()).isEqualTo(8);
        assertThat(snapshot.averageBudgetUtilization())
                .isEqualByComparingTo(new BigDecimal("0.95000000"));
        assertThat(snapshot.averageProviderRating())
                .isEqualByComparingTo(new BigDecimal("2.0000"));
        assertThat(snapshot.verifiedFactCoverage())
                .isEqualByComparingTo(new BigDecimal("0.50000000"));

        PropertyVitalSnapshot latest = vitals.latest(access, f.propertyId());
        assertThat(latest.id()).isEqualTo(snapshot.id());

        assertThatThrownBy(() -> jdbc.update(
                "update vitals.property_vital_snapshot set overall_status = 'GREEN' where id = ?",
                snapshot.id()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesFalseGreenWhenTooFewDimensionsAreKnown() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        seedWorkspacePropertyManagement(workspace, property, actor);

        AccessContext access = new AccessContext(
                actor, "low-coverage-test", workspace, AccessPurpose.PROPERTY_MANAGEMENT
        );

        PropertyVitalSnapshot snapshot = vitals.assess(
                access, property, UUID.randomUUID()
        );

        assertThat(snapshot.obligationStatus()).isEqualTo(VitalStatus.GREEN);
        assertThat(snapshot.guardianStatus()).isEqualTo(VitalStatus.GREEN);
        assertThat(snapshot.knownDimensionCount()).isEqualTo(2);
        assertThat(snapshot.overallStatus()).isEqualTo(VitalStatus.UNKNOWN);
    }

    private Fixture seedOperationalHistory() {
        UUID workspace = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID obligation = UUID.randomUUID();
        UUID signal = UUID.randomUUID();
        UUID action = UUID.randomUUID();
        UUID workOrder = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        UUID providerParty = UUID.randomUUID();
        UUID quote = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        Instant now = Instant.now();

        seedWorkspacePropertyManagement(workspace, property, actor);

        jdbc.update("""
                insert into property.fact
                    (id, property_id, fact_key, value_json, truth_status,
                     source_type, confidence, created_at)
                values
                    (?,?,?,?::jsonb,?,?,?,?),
                    (?,?,?,to_jsonb(?::text),?,?,?,?)
                """,
                UUID.randomUUID(), property, "area_sqm", "420", "VERIFIED",
                "GOVERNMENT_RECORD", 1.0, utc(now.minusSeconds(600)),
                UUID.randomUUID(), property, "condition", "GOOD", "DECLARED",
                "OWNER", 0.8, utc(now.minusSeconds(500))
        );

        jdbc.update("""
                insert into ops.obligation
                    (id, workspace_id, property_id, obligation_type, title, due_at,
                     priority, status, source_type, source_reference, created_by,
                     created_at, version)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                obligation, workspace, property, "SAFETY_INSPECTION",
                "Overdue safety inspection", utc(now.minusSeconds(86400)),
                "CRITICAL", "OPEN", "INSPECTION_PLAN", "inspection-plan-1",
                actor, utc(now.minusSeconds(172800)), 0
        );

        jdbc.update("""
                insert into ops.guardian_signal
                    (id, workspace_id, property_id, obligation_id, signal_type,
                     severity, status, message, recommended_action, detected_at,
                     evidence, version)
                values (?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
                """,
                signal, workspace, property, obligation, "OBLIGATION_DUE",
                "CRITICAL", "OPEN", "Safety inspection overdue",
                "FULFILL_OBLIGATION", utc(now.minusSeconds(3600)), "{}", 0
        );

        jdbc.update("""
                insert into ops.action_item
                    (id, workspace_id, property_id, obligation_id, guardian_signal_id,
                     action_type, title, status, due_at, assigned_actor_id,
                     created_at, version)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                action, workspace, property, obligation, signal,
                "FULFILL_OBLIGATION", "Fulfill safety inspection", "COMPLETED",
                utc(now.minusSeconds(86400)), actor, utc(now.minusSeconds(3500)), 1
        );

        jdbc.update("""
                insert into ops.work_order
                    (id, workspace_id, property_id, action_item_id, category, title,
                     scope_description, status, provider_party_id, estimated_cost,
                     approved_budget, actual_cost, currency, approval_actor_id,
                     approved_at, started_at, completion_submitted_at,
                     completion_evidence_id, completion_note, completed_at,
                     created_by, created_at, version)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                workOrder, workspace, property, action, "SAFETY", "Safety inspection",
                "Inspect and document safety condition", "COMPLETED", providerParty,
                new BigDecimal("900.00"), new BigDecimal("1000.00"),
                new BigDecimal("950.00"), "SAR", actor,
                utc(now.minusSeconds(3000)), utc(now.minusSeconds(2500)),
                utc(now.minusSeconds(1200)), evidence, "Verified completion",
                utc(now.minusSeconds(900)), actor, utc(now.minusSeconds(4000)), 5
        );

        jdbc.update("""
                insert into service_graph.provider
                    (id, workspace_id, provider_party_id, display_name, status,
                     verification_status, verification_evidence_id, verified_at,
                     created_at, version)
                values (?,?,?,?,?,?,?,?,?,?)
                """,
                provider, workspace, providerParty, "Vitals Provider", "ACTIVE",
                "VERIFIED", UUID.randomUUID(), utc(now.minusSeconds(7200)),
                utc(now.minusSeconds(8000)), 1
        );

        jdbc.update("""
                insert into service_graph.quote
                    (id, workspace_id, work_order_id, provider_id, provider_party_id,
                     amount, currency, lead_time_days, scope_note, status,
                     submitted_at, selected_by, selected_at, version)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                quote, workspace, workOrder, provider, providerParty,
                new BigDecimal("1000.00"), "SAR", 1, "Inspection quote", "SELECTED",
                utc(now.minusSeconds(5000)), actor, utc(now.minusSeconds(4500)), 1
        );

        jdbc.update("""
                insert into service_graph.provider_outcome
                    (id, workspace_id, work_order_id, quote_id, provider_id,
                     quoted_amount, actual_cost, cost_variance, rating, outcome_note,
                     recorded_by, recorded_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), workspace, workOrder, quote, provider,
                new BigDecimal("1000.00"), new BigDecimal("950.00"),
                new BigDecimal("-50.00"), 2, "Completed but quality below target",
                actor, utc(now.minusSeconds(600))
        );

        return new Fixture(workspace, property, actor);
    }

    private void seedWorkspacePropertyManagement(
            UUID workspace,
            UUID property,
            UUID actor
    ) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspace, "PERSONAL", "Vitals Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                property, workspace, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
        jdbc.update("""
                insert into ops.management_enrollment
                    (id, workspace_id, property_id, manager_actor_id, status,
                     activated_at, version)
                values (?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), workspace, property, actor, "ACTIVE",
                utc(Instant.now()), 0
        );
    }

    private OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    record Fixture(UUID workspaceId, UUID propertyId, UUID actorId) {}
}
