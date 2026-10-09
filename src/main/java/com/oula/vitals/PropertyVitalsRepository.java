package com.oula.vitals;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class PropertyVitalsRepository {
    private final JdbcClient jdbc;

    PropertyVitalsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void requireManagedProperty(UUID workspaceId, UUID propertyId) {
        Integer count = jdbc.sql("""
                select count(*)
                  from property.asset p
                  join ops.management_enrollment m
                    on m.workspace_id = p.workspace_id
                   and m.property_id = p.id
                   and m.status = 'ACTIVE'
                 where p.workspace_id = :workspaceId
                   and p.id = :propertyId
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query(Integer.class)
                .single();
        if (count == null || count != 1) {
            throw new NoSuchElementException("managed property not found");
        }
    }

    PropertyVitalsPolicy activePolicy() {
        return jdbc.sql("""
                select policy_key, version,
                       cost_utilization_amber, cost_utilization_red,
                       provider_rating_amber, provider_rating_red,
                       provider_cost_variance_amber, provider_cost_variance_red,
                       truth_coverage_amber, truth_coverage_red,
                       freshness_amber_days, freshness_red_days, minimum_known_dimensions
                  from vitals.policy
                 where status = 'ACTIVE'
                   and effective_from <= now()
                   and (effective_to is null or effective_to > now())
                 order by effective_from desc
                 limit 1
                """)
                .query((rs, rowNum) -> new PropertyVitalsPolicy(
                        rs.getString("policy_key"),
                        rs.getString("version"),
                        rs.getBigDecimal("cost_utilization_amber"),
                        rs.getBigDecimal("cost_utilization_red"),
                        rs.getBigDecimal("provider_rating_amber"),
                        rs.getBigDecimal("provider_rating_red"),
                        rs.getBigDecimal("provider_cost_variance_amber"),
                        rs.getBigDecimal("provider_cost_variance_red"),
                        rs.getBigDecimal("truth_coverage_amber"),
                        rs.getBigDecimal("truth_coverage_red"),
                        rs.getInt("freshness_amber_days"),
                        rs.getInt("freshness_red_days"),
                        rs.getInt("minimum_known_dimensions")
                ))
                .optional()
                .orElseThrow(() -> new IllegalStateException("no active property vitals policy"));
    }

    SourceMetrics source(UUID workspaceId, UUID propertyId, Instant now) {
        Counts obligation = jdbc.sql("""
                select
                    count(*) filter (where status = 'OPEN') as open_count,
                    count(*) filter (where status = 'OPEN' and due_at < :now) as overdue_count
                  from ops.obligation
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("now", utc(now))
                .query((rs, rowNum) -> new Counts(
                        rs.getInt("open_count"), rs.getInt("overdue_count")
                ))
                .single();

        Counts guardian = jdbc.sql("""
                select
                    count(*) filter (where status = 'OPEN') as open_count,
                    count(*) filter (where status = 'OPEN' and severity = 'CRITICAL') as overdue_count
                  from ops.guardian_signal
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> new Counts(
                        rs.getInt("open_count"), rs.getInt("overdue_count")
                ))
                .single();

        WorkMetrics work = jdbc.sql("""
                select
                    count(*) filter (
                        where w.status not in ('COMPLETED','CANCELLED')
                    ) as open_count,
                    count(*) filter (
                        where w.status not in ('COMPLETED','CANCELLED')
                          and a.due_at is not null
                          and a.due_at < :now
                    ) as overdue_count,
                    count(*) filter (where w.status = 'COMPLETED') as completed_count,
                    count(*) filter (where w.status = 'COMPLETION_REVIEW') as review_count,
                    count(*) filter (
                        where w.status = 'COMPLETED'
                          and w.completion_evidence_id is null
                    ) as missing_evidence_count,
                    avg(
                        case
                            when w.status = 'COMPLETED'
                             and w.actual_cost is not null
                             and w.approved_budget is not null
                             and w.approved_budget > 0
                            then w.actual_cost / w.approved_budget
                            else null
                        end
                    ) as avg_budget_utilization
                  from ops.work_order w
                  left join ops.action_item a on a.id = w.action_item_id
                 where w.workspace_id = :workspaceId
                   and w.property_id = :propertyId
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("now", utc(now))
                .query((rs, rowNum) -> new WorkMetrics(
                        rs.getInt("open_count"),
                        rs.getInt("overdue_count"),
                        rs.getInt("completed_count"),
                        rs.getInt("review_count"),
                        rs.getInt("missing_evidence_count"),
                        rs.getBigDecimal("avg_budget_utilization")
                ))
                .single();

        ProviderMetrics provider = jdbc.sql("""
                select
                    avg(rating::numeric) as avg_rating,
                    avg(
                        case
                            when quoted_amount > 0
                            then abs(cost_variance) / quoted_amount
                            else null
                        end
                    ) as avg_cost_variance_ratio
                  from service_graph.provider_outcome po
                  join ops.work_order w on w.id = po.work_order_id
                 where po.workspace_id = :workspaceId
                   and w.property_id = :propertyId
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> new ProviderMetrics(
                        rs.getBigDecimal("avg_rating"),
                        rs.getBigDecimal("avg_cost_variance_ratio")
                ))
                .single();

        TruthMetrics truth = jdbc.sql("""
                select
                    count(*) as fact_count,
                    count(*) filter (where truth_status = 'VERIFIED') as verified_count,
                    max(created_at) as latest_fact_at
                  from property.fact
                 where property_id = :propertyId
                """)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> {
                    int facts = rs.getInt("fact_count");
                    int verified = rs.getInt("verified_count");
                    BigDecimal coverage = facts == 0
                            ? null
                            : BigDecimal.valueOf(verified)
                                    .divide(BigDecimal.valueOf(facts), 6, java.math.RoundingMode.HALF_UP);
                    return new TruthMetrics(
                            coverage,
                            instant(rs.getObject("latest_fact_at", OffsetDateTime.class))
                    );
                })
                .single();

        Instant latestOperational = jdbc.sql("""
                select max(activity_at)
                  from (
                        select created_at as activity_at
                          from ops.obligation
                         where workspace_id = :workspaceId and property_id = :propertyId
                        union all
                        select detected_at
                          from ops.guardian_signal
                         where workspace_id = :workspaceId and property_id = :propertyId
                        union all
                        select created_at
                          from ops.action_item
                         where workspace_id = :workspaceId and property_id = :propertyId
                        union all
                        select coalesce(completed_at, completion_submitted_at, started_at, approved_at, created_at)
                          from ops.work_order
                         where workspace_id = :workspaceId and property_id = :propertyId
                        union all
                        select po.recorded_at
                          from service_graph.provider_outcome po
                          join ops.work_order w on w.id = po.work_order_id
                         where po.workspace_id = :workspaceId and w.property_id = :propertyId
                  ) activity
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant)
                .orElse(null);

        return new SourceMetrics(
                obligation.openCount(), obligation.secondaryCount(),
                guardian.openCount(), guardian.secondaryCount(),
                work.openCount(), work.overdueCount(), work.completedCount(),
                work.reviewCount(), work.missingEvidenceCount(), work.averageBudgetUtilization(),
                provider.averageRating(), provider.averageCostVarianceRatio(),
                truth.verifiedFactCoverage(), latestOperational, truth.latestPropertyFactAt()
        );
    }

    void insert(PropertyVitalSnapshot snapshot) {
        jdbc.sql("""
                insert into vitals.property_vital_snapshot (
                    id, workspace_id, property_id, policy_key, policy_version,
                    overall_status, obligation_status, guardian_status, execution_status,
                    cost_status, provider_status, evidence_status, truth_status, freshness_status,
                    known_dimension_count, open_obligations, overdue_obligations, open_guardian_signals,
                    critical_guardian_signals, open_work_orders, overdue_work_orders,
                    completed_work_orders, completion_review_work_orders,
                    missing_completion_evidence, average_budget_utilization,
                    average_provider_rating, average_provider_cost_variance_ratio,
                    verified_fact_coverage, latest_operational_activity_at,
                    latest_property_fact_at, assessed_by, assessed_at
                ) values (
                    :id, :workspaceId, :propertyId, :policyKey, :policyVersion,
                    :overallStatus, :obligationStatus, :guardianStatus, :executionStatus,
                    :costStatus, :providerStatus, :evidenceStatus, :truthStatus, :freshnessStatus,
                    :knownDimensionCount, :openObligations, :overdueObligations, :openGuardianSignals,
                    :criticalGuardianSignals, :openWorkOrders, :overdueWorkOrders,
                    :completedWorkOrders, :completionReviewWorkOrders,
                    :missingCompletionEvidence, :averageBudgetUtilization,
                    :averageProviderRating, :averageProviderCostVarianceRatio,
                    :verifiedFactCoverage, :latestOperationalActivityAt,
                    :latestPropertyFactAt, :assessedBy, :assessedAt
                )
                """)
                .param("id", snapshot.id())
                .param("workspaceId", snapshot.workspaceId())
                .param("propertyId", snapshot.propertyId())
                .param("policyKey", snapshot.policyKey())
                .param("policyVersion", snapshot.policyVersion())
                .param("overallStatus", snapshot.overallStatus().name())
                .param("obligationStatus", snapshot.obligationStatus().name())
                .param("guardianStatus", snapshot.guardianStatus().name())
                .param("executionStatus", snapshot.executionStatus().name())
                .param("costStatus", snapshot.costStatus().name())
                .param("providerStatus", snapshot.providerStatus().name())
                .param("evidenceStatus", snapshot.evidenceStatus().name())
                .param("truthStatus", snapshot.truthStatus().name())
                .param("freshnessStatus", snapshot.freshnessStatus().name())
                .param("knownDimensionCount", snapshot.knownDimensionCount())
                .param("openObligations", snapshot.openObligations())
                .param("overdueObligations", snapshot.overdueObligations())
                .param("openGuardianSignals", snapshot.openGuardianSignals())
                .param("criticalGuardianSignals", snapshot.criticalGuardianSignals())
                .param("openWorkOrders", snapshot.openWorkOrders())
                .param("overdueWorkOrders", snapshot.overdueWorkOrders())
                .param("completedWorkOrders", snapshot.completedWorkOrders())
                .param("completionReviewWorkOrders", snapshot.completionReviewWorkOrders())
                .param("missingCompletionEvidence", snapshot.missingCompletionEvidence())
                .param("averageBudgetUtilization", snapshot.averageBudgetUtilization())
                .param("averageProviderRating", snapshot.averageProviderRating())
                .param("averageProviderCostVarianceRatio", snapshot.averageProviderCostVarianceRatio())
                .param("verifiedFactCoverage", snapshot.verifiedFactCoverage())
                .param("latestOperationalActivityAt", utc(snapshot.latestOperationalActivityAt()))
                .param("latestPropertyFactAt", utc(snapshot.latestPropertyFactAt()))
                .param("assessedBy", snapshot.assessedBy())
                .param("assessedAt", utc(snapshot.assessedAt()))
                .update();
    }

    PropertyVitalSnapshot latest(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select *
                  from vitals.property_vital_snapshot
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by assessed_at desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> mapSnapshot(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("property vital snapshot not found"));
    }


    java.util.List<PropertyVitalSnapshot> history(
            UUID workspaceId, UUID propertyId, int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("history limit must be between 1 and 100");
        }
        return jdbc.sql("""
                select *
                  from vitals.property_vital_snapshot
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by assessed_at desc, id desc
                 limit :limit
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("limit", limit)
                .query((rs, rowNum) -> mapSnapshot(rs))
                .list();
    }

    private PropertyVitalSnapshot mapSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PropertyVitalSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getString("policy_key"),
                rs.getString("policy_version"),
                VitalStatus.valueOf(rs.getString("overall_status")),
                VitalStatus.valueOf(rs.getString("obligation_status")),
                VitalStatus.valueOf(rs.getString("guardian_status")),
                VitalStatus.valueOf(rs.getString("execution_status")),
                VitalStatus.valueOf(rs.getString("cost_status")),
                VitalStatus.valueOf(rs.getString("provider_status")),
                VitalStatus.valueOf(rs.getString("evidence_status")),
                VitalStatus.valueOf(rs.getString("truth_status")),
                VitalStatus.valueOf(rs.getString("freshness_status")),
                rs.getInt("known_dimension_count"),
                rs.getInt("open_obligations"),
                rs.getInt("overdue_obligations"),
                rs.getInt("open_guardian_signals"),
                rs.getInt("critical_guardian_signals"),
                rs.getInt("open_work_orders"),
                rs.getInt("overdue_work_orders"),
                rs.getInt("completed_work_orders"),
                rs.getInt("completion_review_work_orders"),
                rs.getInt("missing_completion_evidence"),
                rs.getBigDecimal("average_budget_utilization"),
                rs.getBigDecimal("average_provider_rating"),
                rs.getBigDecimal("average_provider_cost_variance_ratio"),
                rs.getBigDecimal("verified_fact_coverage"),
                instant(rs.getObject("latest_operational_activity_at", OffsetDateTime.class)),
                instant(rs.getObject("latest_property_fact_at", OffsetDateTime.class)),
                rs.getObject("assessed_by", UUID.class),
                instant(rs.getObject("assessed_at", OffsetDateTime.class))
        );
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    record Counts(int openCount, int secondaryCount) {}

    record WorkMetrics(
            int openCount,
            int overdueCount,
            int completedCount,
            int reviewCount,
            int missingEvidenceCount,
            BigDecimal averageBudgetUtilization
    ) {}

    record ProviderMetrics(
            BigDecimal averageRating,
            BigDecimal averageCostVarianceRatio
    ) {}

    record TruthMetrics(
            BigDecimal verifiedFactCoverage,
            Instant latestPropertyFactAt
    ) {}

    record SourceMetrics(
            int openObligations,
            int overdueObligations,
            int openGuardianSignals,
            int criticalGuardianSignals,
            int openWorkOrders,
            int overdueWorkOrders,
            int completedWorkOrders,
            int completionReviewWorkOrders,
            int missingCompletionEvidence,
            BigDecimal averageBudgetUtilization,
            BigDecimal averageProviderRating,
            BigDecimal averageProviderCostVarianceRatio,
            BigDecimal verifiedFactCoverage,
            Instant latestOperationalActivityAt,
            Instant latestPropertyFactAt
    ) {}
}
