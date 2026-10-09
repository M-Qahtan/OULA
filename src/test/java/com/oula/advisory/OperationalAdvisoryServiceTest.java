package com.oula.advisory;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.vitals.PropertyVitalSnapshot;
import com.oula.vitals.PropertyVitalTrend;
import com.oula.vitals.PropertyVitalsService;
import com.oula.vitals.VitalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OperationalAdvisoryServiceTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID property = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T19:00:00Z");
    private final AccessContext access = new AccessContext(
            actor, "advisor-test", workspace, AccessPurpose.PROPERTY_MANAGEMENT
    );
    private PropertyVitalsService vitals;
    private OperationalAdvisoryService advisory;

    @BeforeEach
    void setUp() {
        vitals = mock(PropertyVitalsService.class);
        advisory = new OperationalAdvisoryService(vitals, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void explainsRedObligationWithCanonicalMetricAndHumanGate() {
        var source = snapshot(now.minusSeconds(60), "v1",
                VitalStatus.RED, VitalStatus.GREEN);
        when(vitals.latest(access, property)).thenReturn(source);
        when(vitals.trend(access, property, 2)).thenReturn(
                trend(source, "WORSENED", "WORSENED"));

        var result = advisory.recommend(access, property);
        assertThat(result.sourceSnapshotId()).isEqualTo(source.id());
        assertThat(result.advisoryRulesVersion()).isEqualTo("operational-advisory-v1");
        assertThat(result.observedTrend()).isEqualTo("WORSENED");
        assertThat(result.assessmentState()).isEqualTo("ASSESSABLE");
        assertThat(result.recommendations()).hasSize(1);
        var item = result.recommendations().getFirst();
        assertThat(item.dimension()).isEqualTo("OBLIGATIONS");
        assertThat(item.priority()).isEqualTo("HIGH");
        assertThat(item.actionCode()).isEqualTo("REVIEW_OBLIGATIONS");
        assertThat(item.executionGate()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(item.trendDirection()).isEqualTo("WORSENED");
        assertThat(item.observedEvidence()).extracting(AdvisoryItem.EvidenceMetric::name)
                .contains("overdueObligations");
    }

    @Test
    void doesNotConflateUnknownWithGreenOrIssueUnapprovedExecution() {
        var source = snapshot(now.minusSeconds(60), "v1",
                VitalStatus.UNKNOWN, VitalStatus.UNKNOWN);
        when(vitals.latest(access, property)).thenReturn(source);
        when(vitals.trend(access, property, 2)).thenReturn(
                new PropertyVitalTrend(property, "NOT_COMPARABLE",
                        source.id(), UUID.randomUUID(), List.of()));

        var result = advisory.recommend(access, property);
        assertThat(result.assessmentState()).isEqualTo("LIMITED_EVIDENCE");
        assertThat(result.limitations()).contains("INSUFFICIENT_OVERALL_EVIDENCE");
        assertThat(result.recommendations()).hasSize(8);
        assertThat(result.recommendations()).allSatisfy(item -> {
            assertThat(item.priority()).isEqualTo("DATA_QUALITY");
            assertThat(item.actionCode()).startsWith("COLLECT_");
            assertThat(item.executionGate()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        });
    }

    @Test
    void staleSourceOnlyRequestsRefreshAndNeverEvaluatesTrend() {
        var source = snapshot(now.minus(DurationDays(4)), "v1",
                VitalStatus.RED, VitalStatus.RED);
        when(vitals.latest(access, property)).thenReturn(source);
        var result = advisory.recommend(access, property);
        assertThat(result.assessmentState()).isEqualTo("STALE");
        assertThat(result.limitations()).containsExactly("SOURCE_ASSESSMENT_STALE");
        assertThat(result.recommendations()).hasSize(1);
        assertThat(result.recommendations().getFirst().actionCode())
                .isEqualTo("REQUEST_NEW_VITAL_ASSESSMENT");
        verify(vitals, never()).trend(any(), any(), anyInt());
    }

    @Test
    void rejectsMismatchedSourceWorkspaceAndUnauthorizedPurpose() {
        var invalidSource = snapshot(now, "v1", VitalStatus.GREEN, VitalStatus.GREEN);
        var foreign = new PropertyVitalSnapshot(
                invalidSource.id(), UUID.randomUUID(), invalidSource.propertyId(),
                invalidSource.policyKey(), invalidSource.policyVersion(),
                invalidSource.overallStatus(), invalidSource.obligationStatus(),
                invalidSource.guardianStatus(), invalidSource.executionStatus(),
                invalidSource.costStatus(), invalidSource.providerStatus(),
                invalidSource.evidenceStatus(), invalidSource.truthStatus(),
                invalidSource.freshnessStatus(), invalidSource.knownDimensionCount(),
                invalidSource.openObligations(), invalidSource.overdueObligations(),
                invalidSource.openGuardianSignals(), invalidSource.criticalGuardianSignals(),
                invalidSource.openWorkOrders(), invalidSource.overdueWorkOrders(),
                invalidSource.completedWorkOrders(), invalidSource.completionReviewWorkOrders(),
                invalidSource.missingCompletionEvidence(),
                invalidSource.averageBudgetUtilization(), invalidSource.averageProviderRating(),
                invalidSource.averageProviderCostVarianceRatio(), invalidSource.verifiedFactCoverage(),
                invalidSource.latestOperationalActivityAt(), invalidSource.latestPropertyFactAt(),
                invalidSource.assessedBy(), invalidSource.assessedAt()
        );
        when(vitals.latest(access, property)).thenReturn(foreign);
        assertThatThrownBy(() -> advisory.recommend(access, property))
                .isInstanceOf(SecurityException.class);

        var denied = new AccessContext(actor, "not-manager", workspace,
                AccessPurpose.PROPERTY_DECISION_SUPPORT);
        assertThatThrownBy(() -> advisory.recommend(denied, property))
                .isInstanceOf(SecurityException.class);
        verify(vitals, never()).latest(denied, property);
    }

    @Test
    void neverConcludesWorseningFromPolicyIncompatibleOrRacingSnapshots() {
        var source = snapshot(now.minusSeconds(20), "v2",
                VitalStatus.AMBER, VitalStatus.GREEN);
        when(vitals.latest(access, property)).thenReturn(source);
        when(vitals.trend(access, property, 2))
                .thenReturn(new PropertyVitalTrend(property, "POLICY_NOT_COMPARABLE",
                        source.id(), UUID.randomUUID(), List.of()))
                .thenReturn(new PropertyVitalTrend(property, "WORSENED",
                        UUID.randomUUID(), UUID.randomUUID(), List.of()));
        var policy = advisory.recommend(access, property);
        assertThat(policy.limitations()).contains("TREND_POLICY_MISMATCH");
        assertThat(policy.recommendations().getFirst().trendDirection()).isEqualTo("NOT_AVAILABLE");

        var racing = advisory.recommend(access, property);
        assertThat(racing.observedTrend()).isEqualTo("NOT_COMPARABLE");
        assertThat(racing.limitations()).contains("SOURCE_CHANGED_DURING_READ");
        assertThat(racing.recommendations().getFirst().executionGate())
                .isEqualTo("HUMAN_REVIEW_REQUIRED");
    }

    private java.time.Duration DurationDays(int days) {
        return java.time.Duration.ofDays(days);
    }

    private PropertyVitalTrend trend(
            PropertyVitalSnapshot s, String direction, String firstDelta
    ) {
        return new PropertyVitalTrend(property, direction, s.id(), UUID.randomUUID(),
                List.of(new PropertyVitalTrend.DimensionChange(
                        "OBLIGATIONS", VitalStatus.GREEN, s.obligationStatus(), firstDelta)));
    }

    private PropertyVitalSnapshot snapshot(
            Instant assessedAt, String policyVersion, VitalStatus obligation, VitalStatus guardian
    ) {
        boolean unknown = obligation == VitalStatus.UNKNOWN;
        VitalStatus remaining = unknown ? VitalStatus.UNKNOWN : VitalStatus.GREEN;
        VitalStatus overall = unknown ? VitalStatus.UNKNOWN
                : obligation == VitalStatus.RED ? VitalStatus.RED
                : obligation == VitalStatus.AMBER ? VitalStatus.AMBER : VitalStatus.GREEN;
        return new PropertyVitalSnapshot(
                UUID.randomUUID(), workspace, property, "property-vitals", policyVersion,
                overall, obligation, guardian,
                remaining, remaining, remaining, remaining, remaining, remaining,
                unknown ? 0 : 8,
                3, 2, 0, 0, 0, 0, 0, 0, 0,
                (BigDecimal) null, null, null, null,
                assessedAt, assessedAt, actor, assessedAt
        );
    }
}
