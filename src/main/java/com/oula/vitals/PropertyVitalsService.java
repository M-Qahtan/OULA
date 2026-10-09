package com.oula.vitals;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyVitalsService {
    private static final Set<AccessPurpose> ALLOWED_PURPOSES = Set.of(
            AccessPurpose.PROPERTY_MANAGEMENT,
            AccessPurpose.AUTONOMOUS_EXECUTION
    );

    private final PropertyVitalsRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public PropertyVitalsService(
            PropertyVitalsRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public PropertyVitalSnapshot assess(
            AccessContext access,
            UUID propertyId,
            UUID correlationId
    ) {
        requirePurpose(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(correlationId, "correlationId");

        repository.requireManagedProperty(access.workspaceId(), propertyId);
        PropertyVitalsPolicy policy = repository.activePolicy();
        Instant now = clock.instant();
        PropertyVitalsRepository.SourceMetrics source =
                repository.source(access.workspaceId(), propertyId, now);

        VitalStatus obligation = source.overdueObligations() > 0
                ? VitalStatus.RED
                : source.openObligations() > 0 ? VitalStatus.AMBER : VitalStatus.GREEN;

        VitalStatus guardian = source.criticalGuardianSignals() > 0
                ? VitalStatus.RED
                : source.openGuardianSignals() > 0 ? VitalStatus.AMBER : VitalStatus.GREEN;

        VitalStatus execution = source.overdueWorkOrders() > 0
                ? VitalStatus.RED
                : (source.openWorkOrders() > 0 || source.completionReviewWorkOrders() > 0)
                    ? VitalStatus.AMBER
                    : source.completedWorkOrders() > 0 ? VitalStatus.GREEN : VitalStatus.UNKNOWN;

        VitalStatus cost = classifyHighBad(
                source.averageBudgetUtilization(),
                policy.costUtilizationAmber(),
                policy.costUtilizationRed()
        );

        VitalStatus provider = classifyProvider(
                source.averageProviderRating(),
                source.averageProviderCostVarianceRatio(),
                policy
        );

        VitalStatus evidence = source.missingCompletionEvidence() > 0
                ? VitalStatus.RED
                : source.completionReviewWorkOrders() > 0
                    ? VitalStatus.AMBER
                    : source.completedWorkOrders() > 0 ? VitalStatus.GREEN : VitalStatus.UNKNOWN;

        VitalStatus truth = classifyLowBad(
                source.verifiedFactCoverage(),
                policy.truthCoverageAmber(),
                policy.truthCoverageRed()
        );

        Instant freshest = max(
                source.latestOperationalActivityAt(),
                source.latestPropertyFactAt()
        );
        VitalStatus freshness = classifyFreshness(freshest, now, policy);

        VitalStatus overall = overall(
                obligation, guardian, execution, cost,
                provider, evidence, truth, freshness
        );

        PropertyVitalSnapshot snapshot = new PropertyVitalSnapshot(
                UuidV7.next(),
                access.workspaceId(),
                propertyId,
                policy.policyKey(),
                policy.version(),
                overall,
                obligation,
                guardian,
                execution,
                cost,
                provider,
                evidence,
                truth,
                freshness,
                source.openObligations(),
                source.overdueObligations(),
                source.openGuardianSignals(),
                source.criticalGuardianSignals(),
                source.openWorkOrders(),
                source.overdueWorkOrders(),
                source.completedWorkOrders(),
                source.completionReviewWorkOrders(),
                source.missingCompletionEvidence(),
                source.averageBudgetUtilization(),
                source.averageProviderRating(),
                source.averageProviderCostVarianceRatio(),
                source.verifiedFactCoverage(),
                source.latestOperationalActivityAt(),
                source.latestPropertyFactAt(),
                access.actorId(),
                now
        );
        repository.insert(snapshot);

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("propertyId", propertyId);
        details.put("policyKey", snapshot.policyKey());
        details.put("policyVersion", snapshot.policyVersion());
        details.put("overallStatus", snapshot.overallStatus().name());
        details.put("obligationStatus", snapshot.obligationStatus().name());
        details.put("guardianStatus", snapshot.guardianStatus().name());
        details.put("executionStatus", snapshot.executionStatus().name());
        details.put("costStatus", snapshot.costStatus().name());
        details.put("providerStatus", snapshot.providerStatus().name());
        details.put("evidenceStatus", snapshot.evidenceStatus().name());
        details.put("truthStatus", snapshot.truthStatus().name());
        details.put("freshnessStatus", snapshot.freshnessStatus().name());

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "PROPERTY_VITALS_ASSESSED",
                "PropertyVitalSnapshot",
                snapshot.id(),
                correlationId,
                details
        );
        outbox.append(
                "vitals.property.assessed.v1",
                "PropertyVitalSnapshot",
                snapshot.id(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
        return snapshot;
    }

    @Transactional(readOnly = true)
    public PropertyVitalSnapshot latest(AccessContext access, UUID propertyId) {
        requirePurpose(access);
        repository.requireManagedProperty(access.workspaceId(), propertyId);
        return repository.latest(access.workspaceId(), propertyId);
    }

    private VitalStatus classifyHighBad(
            BigDecimal value,
            BigDecimal amberThreshold,
            BigDecimal redThreshold
    ) {
        if (value == null) return VitalStatus.UNKNOWN;
        if (value.compareTo(redThreshold) > 0) return VitalStatus.RED;
        if (value.compareTo(amberThreshold) > 0) return VitalStatus.AMBER;
        return VitalStatus.GREEN;
    }

    private VitalStatus classifyLowBad(
            BigDecimal value,
            BigDecimal amberThreshold,
            BigDecimal redThreshold
    ) {
        if (value == null) return VitalStatus.UNKNOWN;
        if (value.compareTo(redThreshold) < 0) return VitalStatus.RED;
        if (value.compareTo(amberThreshold) < 0) return VitalStatus.AMBER;
        return VitalStatus.GREEN;
    }

    private VitalStatus classifyProvider(
            BigDecimal rating,
            BigDecimal variance,
            PropertyVitalsPolicy policy
    ) {
        if (rating == null && variance == null) return VitalStatus.UNKNOWN;

        boolean red = (rating != null && rating.compareTo(policy.providerRatingRed()) < 0)
                || (variance != null
                && variance.compareTo(policy.providerCostVarianceRed()) > 0);
        if (red) return VitalStatus.RED;

        boolean amber = (rating != null && rating.compareTo(policy.providerRatingAmber()) < 0)
                || (variance != null
                && variance.compareTo(policy.providerCostVarianceAmber()) > 0);
        return amber ? VitalStatus.AMBER : VitalStatus.GREEN;
    }

    private VitalStatus classifyFreshness(
            Instant freshest,
            Instant now,
            PropertyVitalsPolicy policy
    ) {
        if (freshest == null) return VitalStatus.UNKNOWN;
        long ageDays = Math.max(0, Duration.between(freshest, now).toDays());
        if (ageDays >= policy.freshnessRedDays()) return VitalStatus.RED;
        if (ageDays >= policy.freshnessAmberDays()) return VitalStatus.AMBER;
        return VitalStatus.GREEN;
    }

    private VitalStatus overall(VitalStatus... statuses) {
        return java.util.Arrays.stream(statuses)
                .filter(status -> status != VitalStatus.UNKNOWN)
                .max(Comparator.comparingInt(this::severity))
                .orElse(VitalStatus.UNKNOWN);
    }

    private int severity(VitalStatus status) {
        return switch (status) {
            case GREEN -> 1;
            case AMBER -> 2;
            case RED -> 3;
            case UNKNOWN -> 0;
        };
    }

    private Instant max(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private void requirePurpose(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (!ALLOWED_PURPOSES.contains(access.purpose())) {
            throw new SecurityException("purpose is not authorized for Property Vital Signs");
        }
    }
}
