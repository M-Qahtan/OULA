package com.oula.vitals;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PropertyVitalTrendTest {
    private PropertyVitalsRepository repository;
    private PropertyVitalsService vitals;
    private final UUID workspace = UUID.randomUUID();
    private final UUID property = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final AccessContext access = new AccessContext(
            actor, "vital-trend-test", workspace, AccessPurpose.PROPERTY_MANAGEMENT
    );

    @BeforeEach
    void setUp() {
        repository = mock(PropertyVitalsRepository.class);
        vitals = new PropertyVitalsService(
                repository, mock(AuditWriter.class), mock(OutboxWriter.class)
        );
    }

    @Test
    void preservesNoHistoryAndInsufficientHistory() {
        when(repository.history(workspace, property, 2))
                .thenReturn(List.of())
                .thenReturn(List.of(snapshot("v1", VitalStatus.GREEN, VitalStatus.GREEN)));
        assertThat(vitals.trend(access, property, 2).direction()).isEqualTo("NO_HISTORY");
        var one = vitals.trend(access, property, 2);
        assertThat(one.direction()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(one.previousSnapshotId()).isNull();
        verify(repository, times(2)).requireManagedProperty(workspace, property);
    }

    @Test
    void reportsWorseningFromKnownOperationalEvidence() {
        var previous = snapshot("v1", VitalStatus.GREEN, VitalStatus.GREEN);
        var current = snapshot("v1", VitalStatus.RED, VitalStatus.GREEN);
        when(repository.history(workspace, property, 2)).thenReturn(List.of(current, previous));
        var trend = vitals.trend(access, property, 2);
        assertThat(trend.direction()).isEqualTo("WORSENED");
        assertThat(trend.currentSnapshotId()).isEqualTo(current.id());
        assertThat(trend.dimensions()).hasSize(8);
        assertThat(trend.dimensions().getFirst().direction()).isEqualTo("WORSENED");
    }

    @Test
    void reportsMixedChangesWithoutHidingOppositeSignals() {
        var previous = snapshot("v1", VitalStatus.RED, VitalStatus.GREEN);
        var current = snapshot("v1", VitalStatus.GREEN, VitalStatus.RED);
        when(repository.history(workspace, property, 2)).thenReturn(List.of(current, previous));
        var trend = vitals.trend(access, property, 2);
        assertThat(trend.direction()).isEqualTo("MIXED");
        assertThat(trend.dimensions()).extracting(PropertyVitalTrend.DimensionChange::direction)
                .contains("IMPROVED", "WORSENED");
    }

    @Test
    void refusesComparisonAcrossDifferentPolicyVersions() {
        when(repository.history(workspace, property, 2)).thenReturn(List.of(
                snapshot("v2", VitalStatus.RED, VitalStatus.GREEN),
                snapshot("v1", VitalStatus.GREEN, VitalStatus.GREEN)
        ));
        var result = vitals.trend(access, property, 2);
        assertThat(result.direction()).isEqualTo("POLICY_NOT_COMPARABLE");
        assertThat(result.dimensions()).isEmpty();
    }

    @Test
    void neverCallsUnknownEvidenceAnImprovementOrUnchanged() {
        var before = snapshot("v1", VitalStatus.UNKNOWN, VitalStatus.UNKNOWN);
        var after = snapshot("v1", VitalStatus.UNKNOWN, VitalStatus.UNKNOWN);
        when(repository.history(workspace, property, 2)).thenReturn(List.of(after, before));
        var trend = vitals.trend(access, property, 2);
        assertThat(trend.direction()).isEqualTo("NOT_COMPARABLE");
        assertThat(trend.dimensions()).allMatch(
                d -> d.direction().equals("NOT_COMPARABLE")
        );
    }

    @Test
    void rejectsCrossPurposeAccessBeforeReadingPropertyHistory() {
        AccessContext denied = new AccessContext(
                actor, "denied", workspace, AccessPurpose.PROPERTY_DECISION_SUPPORT
        );
        assertThatThrownBy(() -> vitals.trend(denied, property, 2))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(repository);
    }

    private PropertyVitalSnapshot snapshot(
            String version, VitalStatus obligation, VitalStatus guardian
    ) {
        return new PropertyVitalSnapshot(
                UUID.randomUUID(), workspace, property, "property-vitals", version,
                VitalStatus.UNKNOWN, obligation, guardian,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                obligation == VitalStatus.UNKNOWN ? VitalStatus.UNKNOWN : VitalStatus.GREEN,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, actor, Instant.now()
        );
    }
}
