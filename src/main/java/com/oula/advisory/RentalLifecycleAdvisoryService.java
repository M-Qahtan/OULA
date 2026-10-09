package com.oula.advisory;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.tenancy.*;
import com.oula.vitals.VitalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evidence-grounded read-only rental triage for human Property Guardian workflows.
 * Never asserts legal arrears or bank-confirmed nonpayment and never writes actions.
 */
@Service
public class RentalLifecycleAdvisoryService {
    public static final String RULES_VERSION = "rental-lifecycle-advisory-v1";
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private static final int MAX_UNITS_PER_REQUEST = 250;
    private final TenancyService tenancy;
    private final RentalFinancialService finance;
    private final Clock clock;

    @Autowired
    public RentalLifecycleAdvisoryService(TenancyService tenancy, RentalFinancialService finance) {
        this(tenancy, finance, Clock.systemUTC());
    }

    RentalLifecycleAdvisoryService(TenancyService tenancy, RentalFinancialService finance, Clock clock) {
        this.tenancy = tenancy;
        this.finance = finance;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RentalLifecycleAdvisory recommend(AccessContext access, UUID propertyId) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(propertyId, "propertyId");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT)
            throw new SecurityException("PROPERTY_MANAGEMENT purpose required");

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, RIYADH);
        PropertyOccupancyInsight occupancy = finance.occupancyInsight(access, propertyId, now);
        List<UnitOccupancyView> units = tenancy.occupancyByProperty(access, propertyId);
        if (units.size() != occupancy.totalUnits()) {
            throw new IllegalStateException("unit source changed during assessment; retry");
        }
        if (units.size() > MAX_UNITS_PER_REQUEST)
            throw new IllegalStateException("property exceeds current advisory batch size");

        List<String> limitations = new ArrayList<>(List.of(
                "DOCUMENTARY_COVERAGE_NOT_BANK_SETTLEMENT",
                "NO_LEGAL_NONPAYMENT_DETERMINATION",
                "OCCUPANCY_REFLECTS_RECORDED_HANDOVERS_ONLY",
                "RECOMMENDATIONS_REQUIRE_HUMAN_REVIEW"
        ));
        if (occupancy.unknownUnits() > 0)
            limitations.add("OCCUPANCY_EVIDENCE_INCOMPLETE");

        List<AdvisoryItem> items = new ArrayList<>();
        int activeLeasesReviewed = 0;

        for (UnitOccupancyView unit : units) {
            if ("UNKNOWN".equals(unit.occupancyStatus())) {
                items.add(item("OCCUPANCY_EVIDENCE", VitalStatus.UNKNOWN,
                        "DATA_QUALITY", "VERIFY_UNIT_HANDOVER_RECORD",
                        "No verified check-in or check-out observation establishes physical occupancy.",
                        unit.unitId(), null,
                        facts("unitCode", unit.unitCode(), "recordedOccupancy", "UNKNOWN")));
            } else if ("VACANCY_RECORDED".equals(unit.occupancyStatus())) {
                items.add(item("RECORDED_VACANCY", VitalStatus.AMBER,
                        "MEDIUM", "REVIEW_POST_CHECKOUT_UNIT_STATE",
                        "A check-out was recorded; confirm present unit status before reletting.",
                        unit.unitId(), unit.leaseId(),
                        facts("recordedCheckoutAt", String.valueOf(unit.lastCheckedOutAt()),
                                "unitCode", unit.unitCode())));
            }

            List<Lease> leases = tenancy.leasesForUnit(access, unit.unitId());
            for (Lease lease : leases) {
                if (!Set.of("ACTIVE", "SIGNED").contains(lease.status())) continue;
                long daysToEnd = ChronoUnit.DAYS.between(today, lease.endOn());

                if (daysToEnd <= 60) {
                    // This is a contractual-review priority; no inference about validity or eviction.
                    boolean expiredInSystem = daysToEnd < 0;
                    boolean urgent = daysToEnd <= 14;
                    List<RenewalDecision> decisions =
                            tenancy.renewalHistory(access, lease.id());
                    String lastDecision = decisions.isEmpty() ? "NONE"
                            : decisions.getLast().decision();
                    String action = expiredInSystem
                            ? "VERIFY_CONTRACT_END_STATUS"
                            : "RENEWAL_ACCEPTED".equals(lastDecision)
                                    ? "VERIFY_SIGNED_RENEWAL_INSTRUMENT"
                                    : "RENEWAL_DECLINED".equals(lastDecision)
                                            ? "PLAN_HUMAN_HANDOVER_REVIEW"
                                            : "REVIEW_RENEWAL_DECISION";
                    items.add(item("LEASE_LIFECYCLE",
                            urgent ? VitalStatus.RED : VitalStatus.AMBER,
                            urgent ? "HIGH" : "MEDIUM", action,
                            expiredInSystem
                                    ? "Recorded end date has passed; verify current legal and operational status."
                                    : "Review contractual expiry and recorded renewal decision with authorized parties.",
                            unit.unitId(), lease.id(),
                            facts("daysToRecordedEnd", Long.toString(daysToEnd),
                                    "latestRenewalDecision", lastDecision)));
                }

                if ("ACTIVE".equals(lease.status())) {
                    activeLeasesReviewed++;
                    RentalFinancialSummary financial =
                            finance.financialSummary(access, lease.id(), today);
                    if (financial.installments().isEmpty()) {
                        items.add(item("RENT_SCHEDULE_EVIDENCE", VitalStatus.UNKNOWN,
                                "DATA_QUALITY", "VERIFY_CONTRACTUAL_RENT_SCHEDULE",
                                "Active lease has no recorded installments; do not infer payment status.",
                                unit.unitId(), lease.id(),
                                facts("rentScheduleItems", "0")));
                    } else if (financial.uncoveredDueThroughDate().signum() > 0) {
                        boolean repeated = financial.overdueInstallments() >= 2;
                        items.add(item("DOCUMENTARY_RENT_COVERAGE", VitalStatus.AMBER,
                                repeated ? "HIGH" : "MEDIUM",
                                "REVIEW_RENT_DOCUMENTARY_COVERAGE",
                                "Contractual dues are not fully covered by recorded documents. "
                                + "This is NOT proof of unpaid rent or bank non-settlement.",
                                unit.unitId(), lease.id(),
                                facts("documentaryGapDue", financial.uncoveredDueThroughDate().toPlainString(),
                                        "overdueInstallments", Integer.toString(financial.overdueInstallments()))));
                    }
                }
            }
        }

        items.sort(Comparator
                .comparingInt((AdvisoryItem i) -> priorityRank(i.priority()))
                .thenComparing(AdvisoryItem::dimension)
                .thenComparing(i -> i.observedEvidence().stream()
                        .filter(m -> m.name().equals("unitId"))
                        .map(AdvisoryItem.EvidenceMetric::value).findFirst().orElse("")));
        String state = occupancy.totalUnits() == 0 ? "NO_UNITS"
                : occupancy.knownUnits() == 0 ? "INSUFFICIENT_OCCUPANCY_EVIDENCE"
                : occupancy.unknownUnits() > 0 ? "PARTIAL_OCCUPANCY_EVIDENCE"
                : "EVIDENCE_RECORDED";
        return new RentalLifecycleAdvisory(access.workspaceId(), propertyId,
                RULES_VERSION, now, today, state, occupancy.totalUnits(),
                occupancy.knownUnits(), occupancy.unknownUnits(), activeLeasesReviewed,
                limitations, items);
    }

    private AdvisoryItem item(String dimension, VitalStatus observed,
                              String priority, String action, String reason,
                              UUID unitId, UUID leaseId,
                              List<AdvisoryItem.EvidenceMetric> evidence) {
        List<AdvisoryItem.EvidenceMetric> metrics = new ArrayList<>(evidence);
        metrics.add(new AdvisoryItem.EvidenceMetric("unitId", unitId.toString()));
        if (leaseId != null) metrics.add(new AdvisoryItem.EvidenceMetric("leaseId", leaseId.toString()));
        return new AdvisoryItem(dimension, observed, "NOT_EVALUATED",
                priority, action, reason, "HUMAN_REVIEW_REQUIRED", metrics);
    }

    private List<AdvisoryItem.EvidenceMetric> facts(
            String name1, String value1, String name2, String value2) {
        return List.of(new AdvisoryItem.EvidenceMetric(name1, value1),
                new AdvisoryItem.EvidenceMetric(name2, value2));
    }
    private List<AdvisoryItem.EvidenceMetric> facts(String name, String value) {
        return List.of(new AdvisoryItem.EvidenceMetric(name, value));
    }
    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}
