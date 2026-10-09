package com.oula.interventions;

import com.oula.advisory.AdvisoryItem;
import com.oula.advisory.OperationalAdvisory;
import com.oula.advisory.OperationalAdvisoryService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.OperationsExecutionService;
import com.oula.operations.WorkOrder;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import com.oula.vitals.PropertyVitalSnapshot;
import com.oula.vitals.PropertyVitalsService;
import com.oula.vitals.VitalStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class InterventionService {
    private static final Set<String> HUMAN_DECISIONS =
            Set.of("ACKNOWLEDGED", "DECLINED", "DEFERRED");

    private final InterventionRepository repository;
    private final OperationalAdvisoryService advisor;
    private final PropertyVitalsService vitals;
    private final OperationsExecutionService workOrders;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public InterventionService(
            InterventionRepository repository,
            OperationalAdvisoryService advisor,
            PropertyVitalsService vitals,
            OperationsExecutionService workOrders,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.advisor = advisor;
        this.vitals = vitals;
        this.workOrders = workOrders;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public InterventionReview review(
            AccessContext access, UUID propertyId,
            RecordInterventionReviewCommand command, UUID correlationId
    ) {
        requireHuman(access);
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.sourceSnapshotId(), "sourceSnapshotId");
        Objects.requireNonNull(correlationId, "correlationId");
        text(command.dimension(), "dimension");
        text(command.actionCode(), "actionCode");
        text(command.rationale(), "rationale");

        String decision = command.decision() == null ? "" : command.decision().trim();
        if (!HUMAN_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("unsupported human review decision");
        }

        OperationalAdvisory advisory = advisor.recommend(access, propertyId);
        if ("STALE".equals(advisory.assessmentState())) {
            throw new IllegalStateException("stale advisory requires a fresh source assessment");
        }
        if (!advisory.sourceSnapshotId().equals(command.sourceSnapshotId())) {
            throw new IllegalStateException("review source does not match current advisory");
        }

        AdvisoryItem selected = advisory.recommendations().stream()
                .filter(item -> item.dimension().equals(command.dimension())
                        && item.actionCode().equals(command.actionCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "requested action was not recommended for this source snapshot"));

        // Recheck current source; do not attach the human review to a replaced snapshot.
        if (!vitals.latest(access, propertyId).id().equals(advisory.sourceSnapshotId())) {
            throw new IllegalStateException("a newer vital snapshot replaced the advisory");
        }

        InterventionReview recorded = new InterventionReview(
                UuidV7.next(), access.workspaceId(), propertyId,
                advisory.sourceSnapshotId(), advisory.snapshotPolicyKey(),
                advisory.snapshotPolicyVersion(), advisory.advisoryRulesVersion(),
                selected.dimension(), selected.actionCode(), selected.observedStatus(),
                decision, command.rationale().trim(), access.actorId(), clock.instant()
        );
        repository.insertReview(recorded);
        auditAndEvent(access, correlationId, "INTERVENTION_REVIEW_RECORDED",
                "interventions.review.recorded.v1", "InterventionReview",
                recorded.id(), Map.of(
                        "propertyId", propertyId,
                        "sourceSnapshotId", recorded.sourceSnapshotId(),
                        "dimension", recorded.dimension(), "decision", recorded.decision(),
                        "humanReviewOnly", true));
        return recorded;
    }

    @Transactional
    public InterventionOutcome observe(
            AccessContext access, UUID reviewId,
            RecordInterventionOutcomeCommand command, UUID correlationId
    ) {
        requireHuman(access);
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.afterSnapshotId(), "afterSnapshotId");
        Objects.requireNonNull(correlationId, "correlationId");
        text(command.observationNote(), "observationNote");

        InterventionReview review = repository.lockReview(access.workspaceId(), reviewId);
        PropertyVitalSnapshot before = vitals.byId(
                access, review.propertyId(), review.sourceSnapshotId());
        PropertyVitalSnapshot after = vitals.byId(
                access, review.propertyId(), command.afterSnapshotId());
        if (!after.policyKey().equals(before.policyKey())
                || !after.policyVersion().equals(before.policyVersion())) {
            throw new IllegalStateException("vital policy versions are not comparable");
        }
        Instant now = clock.instant();
        if (!after.assessedAt().isAfter(review.reviewedAt())
                || !after.assessedAt().isAfter(before.assessedAt())
                || after.assessedAt().isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new IllegalStateException("follow-up assessment must occur after human review");
        }

        // Prevent retrospective selection of an older or more flattering observation.
        if (!vitals.latest(access, review.propertyId()).id().equals(after.id())) {
            throw new IllegalStateException("follow-up must reference the latest vital snapshot");
        }

        UUID workOrderId = command.completedWorkOrderId();
        String evidenceLevel = "OBSERVATION_ONLY";
        if (workOrderId != null) {
            if (!"ACKNOWLEDGED".equals(review.decision())) {
                throw new IllegalStateException("only acknowledged advice may link an execution record");
            }
            WorkOrder workOrder = workOrders.get(access, workOrderId);
            if (!workOrder.propertyId().equals(review.propertyId())
                    || !"COMPLETED".equals(workOrder.status())
                    || workOrder.completionEvidenceId() == null
                    || workOrder.completedAt() == null
                    || workOrder.completedAt().isBefore(review.reviewedAt())
                    || workOrder.completedAt().isAfter(after.assessedAt())) {
                throw new IllegalStateException("Work Order is not completed with evidence in review period");
            }
            evidenceLevel = "VERIFIED_WORK_ORDER";
        }

        VitalStatus beforeStatus = dimension(before, review.dimension());
        if (beforeStatus != review.baselineStatus()) {
            throw new IllegalStateException("review baseline status does not match immutable snapshot");
        }
        VitalStatus afterStatus = dimension(after, review.dimension());
        String direction = compare(beforeStatus, afterStatus);

        InterventionOutcome outcome = new InterventionOutcome(
                UuidV7.next(), access.workspaceId(), review.propertyId(), review.id(),
                after.id(), workOrderId, beforeStatus, afterStatus,
                direction, evidenceLevel, command.observationNote().trim(),
                access.actorId(), now
        );
        repository.insertOutcome(outcome);
        auditAndEvent(access, correlationId, "INTERVENTION_OUTCOME_OBSERVED",
                "interventions.outcome.observed.v1", "InterventionOutcome",
                outcome.id(), Map.of(
                        "propertyId", outcome.propertyId(),
                        "reviewId", outcome.reviewId(),
                        "afterSnapshotId", outcome.afterSnapshotId(),
                        "direction", outcome.observedDirection(),
                        "executionEvidenceLevel", outcome.executionEvidenceLevel(),
                        "causalAttribution", "NOT_ESTABLISHED"));
        return outcome;
    }

    @Transactional(readOnly = true)
    public InterventionHistory history(AccessContext access, UUID propertyId) {
        requireHuman(access);
        vitals.latest(access, propertyId);
        return new InterventionHistory(
                propertyId, repository.listReviews(access.workspaceId(), propertyId),
                repository.listOutcomes(access.workspaceId(), propertyId)
        );
    }


    /**
     * Historic intervention evidence for an already-authorized evaluation caller.
     * Does not require a latest Vital snapshot, so never fabricates a zero from
     * a missing snapshot. All queries remain scoped to this workspace/property.
     */
    @Transactional(readOnly = true)
    public InterventionHistory evaluationHistory(AccessContext access, UUID propertyId) {
        requireHuman(access);
        Objects.requireNonNull(propertyId, "propertyId");
        return new InterventionHistory(propertyId,
                repository.listReviews(access.workspaceId(), propertyId),
                repository.listOutcomes(access.workspaceId(), propertyId));
    }

    private VitalStatus dimension(PropertyVitalSnapshot s, String name) {
        return switch (name) {
            case "OBLIGATIONS" -> s.obligationStatus();
            case "GUARDIAN" -> s.guardianStatus();
            case "EXECUTION" -> s.executionStatus();
            case "COST" -> s.costStatus();
            case "PROVIDER" -> s.providerStatus();
            case "EVIDENCE" -> s.evidenceStatus();
            case "TRUTH" -> s.truthStatus();
            case "FRESHNESS" -> s.freshnessStatus();
            default -> throw new IllegalStateException("unsupported historical dimension");
        };
    }

    private String compare(VitalStatus before, VitalStatus after) {
        if (before == VitalStatus.UNKNOWN || after == VitalStatus.UNKNOWN) {
            return "NOT_COMPARABLE";
        }
        int delta = severity(after) - severity(before);
        return delta < 0 ? "IMPROVED" : delta > 0 ? "WORSENED" : "UNCHANGED";
    }

    private int severity(VitalStatus status) {
        return switch (status) {
            case GREEN -> 1;
            case AMBER -> 2;
            case RED -> 3;
            case UNKNOWN -> 0;
        };
    }

    private void auditAndEvent(
            AccessContext access, UUID correlationId, String auditAction,
            String eventType, String aggregateType, UUID aggregateId,
            Map<String, ?> details
    ) {
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, aggregateType,
                aggregateId, correlationId, details);
        outbox.append(eventType, aggregateType, aggregateId, access.workspaceId(),
                correlationId, correlationId, details);
    }

    private void requireHuman(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT is required for human review");
        }
    }

    private void text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
