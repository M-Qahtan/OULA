package com.oula.advisory;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.vitals.PropertyVitalSnapshot;
import com.oula.vitals.PropertyVitalTrend;
import com.oula.vitals.PropertyVitalsService;
import com.oula.vitals.VitalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A deterministic advisory read model, not an agent or an execution gateway.
 * It makes no mutations, funds transfers, compliance grants or external calls.
 */
@Service
public class OperationalAdvisoryService {
    public static final String RULES_VERSION = "operational-advisory-v1";
    private static final Duration MAX_AGE = Duration.ofHours(48);
    private final PropertyVitalsService vitals;
    private final Clock clock;

    @Autowired
    public OperationalAdvisoryService(PropertyVitalsService vitals) {
        this(vitals, Clock.systemUTC());
    }

    OperationalAdvisoryService(PropertyVitalsService vitals, Clock clock) {
        this.vitals = vitals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationalAdvisory recommend(AccessContext access, UUID propertyId) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(propertyId, "propertyId");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
        PropertyVitalSnapshot snapshot = vitals.latest(access, propertyId);
        if (!access.workspaceId().equals(snapshot.workspaceId())
                || !propertyId.equals(snapshot.propertyId())) {
            throw new SecurityException("source snapshot does not match authorized property");
        }
        Instant now = clock.instant();
        List<String> limitations = new ArrayList<>();
        List<AdvisoryItem> items = new ArrayList<>();

        if (snapshot.assessedAt().isBefore(now.minus(MAX_AGE))
                || snapshot.assessedAt().isAfter(now.plus(Duration.ofMinutes(5)))) {
            limitations.add("SOURCE_ASSESSMENT_STALE");
            items.add(new AdvisoryItem(
                    "DATA_FRESHNESS", VitalStatus.UNKNOWN, "NOT_EVALUATED",
                    "DATA_QUALITY", "REQUEST_NEW_VITAL_ASSESSMENT",
                    "Assessment is older than 48 hours or future-dated. Request a new authorized assessment.",
                    "HUMAN_REVIEW_REQUIRED",
                    List.of(new AdvisoryItem.EvidenceMetric("sourceAssessedAt", snapshot.assessedAt().toString()))
            ));
            return result(access, snapshot, now, "STALE", "NOT_EVALUATED", limitations, items);
        }

        PropertyVitalTrend trend = vitals.trend(access, propertyId, 2);
        boolean aligned = snapshot.id().equals(trend.currentSnapshotId());
        String direction = aligned ? trend.direction() : "NOT_COMPARABLE";
        if (!aligned) limitations.add("SOURCE_CHANGED_DURING_READ");
        if ("POLICY_NOT_COMPARABLE".equals(direction)) limitations.add("TREND_POLICY_MISMATCH");
        if ("INSUFFICIENT_HISTORY".equals(direction)) limitations.add("ONLY_ONE_SNAPSHOT");
        if ("NOT_COMPARABLE".equals(direction)) limitations.add("TREND_NOT_COMPARABLE");
        if (snapshot.overallStatus() == VitalStatus.UNKNOWN) {
            limitations.add("INSUFFICIENT_OVERALL_EVIDENCE");
        }

        Map<String, String> deltas = new HashMap<>();
        if (aligned) {
            for (PropertyVitalTrend.DimensionChange change : trend.dimensions()) {
                deltas.put(change.dimension(), change.direction());
            }
        }

        recommend(items, "OBLIGATIONS", snapshot.obligationStatus(), deltas,
                "REVIEW_OBLIGATIONS", "Review due dates, contracts and accountable parties.",
                pairs("openObligations", snapshot.openObligations(),
                        "overdueObligations", snapshot.overdueObligations()));
        recommend(items, "GUARDIAN", snapshot.guardianStatus(), deltas,
                "TRIAGE_GUARDIAN_SIGNALS", "Review unresolved Guardian signals and source evidence.",
                pairs("openGuardianSignals", snapshot.openGuardianSignals(),
                        "criticalGuardianSignals", snapshot.criticalGuardianSignals()));
        recommend(items, "EXECUTION", snapshot.executionStatus(), deltas,
                "REVIEW_WORK_ORDER_BACKLOG", "Review active and overdue Work Orders with their owners.",
                pairs("openWorkOrders", snapshot.openWorkOrders(),
                        "overdueWorkOrders", snapshot.overdueWorkOrders()));
        recommend(items, "COST", snapshot.costStatus(), deltas,
                "RECONCILE_OPERATIONAL_COST", "Compare authorized budgets and evidenced actual costs.",
                one("averageBudgetUtilization", snapshot.averageBudgetUtilization()));
        recommend(items, "PROVIDER", snapshot.providerStatus(), deltas,
                "REVIEW_PROVIDER_OUTCOMES", "Inspect observed provider performance before vendor decisions.",
                combined("averageProviderRating", snapshot.averageProviderRating(),
                        "averageProviderCostVarianceRatio", snapshot.averageProviderCostVarianceRatio()));
        recommend(items, "EVIDENCE", snapshot.evidenceStatus(), deltas,
                "VERIFY_COMPLETION_EVIDENCE", "Inspect pending reviews and missing completion evidence.",
                pairs("completionReviewWorkOrders", snapshot.completionReviewWorkOrders(),
                        "missingCompletionEvidence", snapshot.missingCompletionEvidence()));
        recommend(items, "TRUTH", snapshot.truthStatus(), deltas,
                "VERIFY_PROPERTY_FACT_PROVENANCE", "Verify property facts against authorized source evidence.",
                one("verifiedFactCoverage", snapshot.verifiedFactCoverage()));
        recommend(items, "FRESHNESS", snapshot.freshnessStatus(), deltas,
                "REFRESH_PROPERTY_SOURCE_DATA", "Check recency of operational and property facts.",
                dates(snapshot.latestOperationalActivityAt(), snapshot.latestPropertyFactAt()));

        items.sort(Comparator.comparingInt((AdvisoryItem item) -> rank(item.priority()))
                .thenComparing(AdvisoryItem::dimension));
        String state = snapshot.overallStatus() == VitalStatus.UNKNOWN
                ? "LIMITED_EVIDENCE" : "ASSESSABLE";
        return result(access, snapshot, now, state, direction, limitations, items);
    }

    private OperationalAdvisory result(
            AccessContext access, PropertyVitalSnapshot s, Instant now,
            String state, String direction, List<String> limitations, List<AdvisoryItem> items
    ) {
        return new OperationalAdvisory(
                access.workspaceId(), s.propertyId(), s.id(), s.policyKey(),
                s.policyVersion(), RULES_VERSION, state, direction,
                s.assessedAt(), now, limitations, items
        );
    }

    private void recommend(
            List<AdvisoryItem> list, String dimension, VitalStatus status,
            Map<String,String> deltas, String action, String rationale,
            List<AdvisoryItem.EvidenceMetric> facts
    ) {
        if (status == VitalStatus.GREEN) return;
        String priority = status == VitalStatus.RED ? "HIGH"
                : status == VitalStatus.AMBER ? "MEDIUM" : "DATA_QUALITY";
        String actionCode = status == VitalStatus.UNKNOWN
                ? "COLLECT_" + dimension + "_EVIDENCE" : action;
        String explanation = status == VitalStatus.UNKNOWN
                ? "Insufficient source evidence for " + dimension + ". Obtain authorized observations first."
                : rationale;
        list.add(new AdvisoryItem(dimension, status,
                deltas.getOrDefault(dimension, "NOT_AVAILABLE"), priority,
                actionCode, explanation, "HUMAN_REVIEW_REQUIRED", facts));
    }

    private int rank(String priority) {
        return switch (priority) { case "HIGH" -> 0; case "MEDIUM" -> 1; default -> 2; };
    }

    private List<AdvisoryItem.EvidenceMetric> pairs(
            String a, int aValue, String b, int bValue
    ) {
        return List.of(new AdvisoryItem.EvidenceMetric(a, Integer.toString(aValue)),
                new AdvisoryItem.EvidenceMetric(b, Integer.toString(bValue)));
    }

    private List<AdvisoryItem.EvidenceMetric> one(String name, BigDecimal value) {
        return value == null ? List.of()
                : List.of(new AdvisoryItem.EvidenceMetric(name, value.toPlainString()));
    }

    private List<AdvisoryItem.EvidenceMetric> combined(
            String a, BigDecimal av, String b, BigDecimal bv
    ) {
        List<AdvisoryItem.EvidenceMetric> facts = new ArrayList<>(one(a, av));
        facts.addAll(one(b, bv));
        return List.copyOf(facts);
    }

    private List<AdvisoryItem.EvidenceMetric> dates(Instant operational, Instant property) {
        List<AdvisoryItem.EvidenceMetric> facts = new ArrayList<>();
        if (operational != null)
            facts.add(new AdvisoryItem.EvidenceMetric("latestOperationalActivityAt", operational.toString()));
        if (property != null)
            facts.add(new AdvisoryItem.EvidenceMetric("latestPropertyFactAt", property.toString()));
        return List.copyOf(facts);
    }
}
