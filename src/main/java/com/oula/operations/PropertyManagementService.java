package com.oula.operations;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyManagementService {
    private static final Set<String> PRIORITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final PropertyManagementRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public PropertyManagementService(
            PropertyManagementRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ManagementEnrollment enroll(
            AccessContext access,
            UUID propertyId,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(correlationId, "correlationId");

        repository.lockProperty(access.workspaceId(), propertyId);
        ManagementEnrollment existing =
                repository.activeEnrollment(access.workspaceId(), propertyId);
        if (existing != null) {
            return existing;
        }

        Instant now = clock.instant();
        ManagementEnrollment enrollment = new ManagementEnrollment(
                UuidV7.next(),
                access.workspaceId(),
                propertyId,
                access.actorId(),
                "ACTIVE",
                now,
                null,
                0
        );
        repository.insertEnrollment(enrollment);

        Map<String, Object> details = Map.of(
                "propertyId", propertyId,
                "status", "ACTIVE"
        );
        audit(access, "PROPERTY_MANAGEMENT_ENROLLED", "ManagementEnrollment",
                enrollment.id(), correlationId, details);
        outbox.append(
                "operations.property_management.enrolled.v1",
                "ManagementEnrollment",
                enrollment.id(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
        return enrollment;
    }

    @Transactional
    public Obligation createObligation(
            AccessContext access,
            UUID propertyId,
            CreateObligationCommand command,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.dueAt(), "dueAt");
        Objects.requireNonNull(correlationId, "correlationId");
        requireText(command.obligationType(), "obligationType");
        requireText(command.title(), "title");
        requireText(command.priority(), "priority");
        requireText(command.sourceType(), "sourceType");
        if (!PRIORITIES.contains(command.priority())) {
            throw new IllegalArgumentException("unsupported priority");
        }

        repository.lockProperty(access.workspaceId(), propertyId);
        if (repository.activeEnrollment(access.workspaceId(), propertyId) == null) {
            throw new IllegalStateException("property is not enrolled in management");
        }

        Instant now = clock.instant();
        Obligation obligation = new Obligation(
                UuidV7.next(),
                access.workspaceId(),
                propertyId,
                command.obligationType(),
                command.title(),
                command.dueAt(),
                command.priority(),
                "OPEN",
                command.sourceType(),
                command.sourceReference(),
                access.actorId(),
                now,
                null,
                0
        );
        repository.insertObligation(obligation);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("propertyId", propertyId);
        details.put("obligationType", obligation.obligationType());
        details.put("priority", obligation.priority());
        details.put("dueAt", obligation.dueAt().toString());

        audit(access, "PROPERTY_OBLIGATION_CREATED", "Obligation",
                obligation.id(), correlationId, details);
        outbox.append(
                "operations.obligation.created.v1",
                "Obligation",
                obligation.id(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
        return obligation;
    }

    @Transactional
    public GuardianAssessment assess(
            AccessContext access,
            UUID propertyId,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(correlationId, "correlationId");

        ManagementEnrollment enrollment =
                repository.activeEnrollment(access.workspaceId(), propertyId);
        if (enrollment == null) {
            throw new IllegalStateException("property is not enrolled in management");
        }

        Instant now = clock.instant();
        var obligations = repository.dueObligations(
                access.workspaceId(),
                propertyId,
                now.plus(Duration.ofDays(30))
        );

        int signalsCreated = 0;
        int actionsCreated = 0;
        for (Obligation obligation : obligations) {
            GuardianSignal signal = repository.signalForObligation(obligation.id());
            if (signal == null) {
                String severity = severity(obligation.dueAt(), now);
                signal = repository.insertSignal(
                        UuidV7.next(),
                        obligation,
                        severity,
                        "Obligation due: " + obligation.title(),
                        "FULFILL_OBLIGATION",
                        now
                );
                signalsCreated++;
            }

            ActionItem action = repository.actionForObligation(obligation.id());
            if (action == null) {
                repository.insertAction(
                        UuidV7.next(),
                        obligation,
                        signal,
                        enrollment.managerActorId(),
                        now
                );
                actionsCreated++;
            }
        }

        GuardianAssessment assessment = new GuardianAssessment(
                propertyId,
                now,
                obligations.size(),
                signalsCreated,
                actionsCreated
        );

        Map<String, Object> details = Map.of(
                "propertyId", propertyId,
                "obligationsEvaluated", assessment.obligationsEvaluated(),
                "signalsCreated", assessment.signalsCreated(),
                "actionsCreated", assessment.actionsCreated()
        );
        audit(access, "PROPERTY_GUARDIAN_ASSESSED", "Property",
                propertyId, correlationId, details);
        outbox.append(
                "operations.guardian.assessed.v1",
                "Property",
                propertyId,
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
        return assessment;
    }

    @Transactional(readOnly = true)
    public ManagementOverview overview(AccessContext access, UUID propertyId) {
        requireManagement(access);
        repository.lockProperty(access.workspaceId(), propertyId);
        ManagementEnrollment enrollment =
                repository.activeEnrollment(access.workspaceId(), propertyId);
        if (enrollment == null) {
            throw new IllegalStateException("property is not enrolled in management");
        }
        return new ManagementOverview(
                propertyId,
                enrollment,
                repository.obligations(access.workspaceId(), propertyId),
                repository.signals(access.workspaceId(), propertyId),
                repository.actions(access.workspaceId(), propertyId)
        );
    }

    @Transactional
    public ActionItem completeAction(
            AccessContext access,
            UUID actionId,
            String resolutionNote,
            UUID correlationId
    ) {
        requireManagement(access);
        requireText(resolutionNote, "resolutionNote");
        ActionItem current = repository.lockAction(access.workspaceId(), actionId);
        if ("COMPLETED".equals(current.status())) {
            return current;
        }

        Instant now = clock.instant();
        ActionItem completed = repository.completeAction(current, resolutionNote, now);

        Map<String, Object> details = Map.of(
                "propertyId", completed.propertyId(),
                "actionType", completed.actionType(),
                "status", completed.status()
        );
        audit(access, "PROPERTY_ACTION_COMPLETED", "ActionItem",
                completed.id(), correlationId, details);
        outbox.append(
                "operations.action.completed.v1",
                "ActionItem",
                completed.id(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
        return completed;
    }

    private String severity(Instant dueAt, Instant now) {
        Duration remaining = Duration.between(now, dueAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return "CRITICAL";
        }
        if (remaining.compareTo(Duration.ofDays(3)) <= 0) {
            return "HIGH";
        }
        if (remaining.compareTo(Duration.ofDays(7)) <= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void requireManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void audit(
            AccessContext access,
            String action,
            String aggregateType,
            UUID aggregateId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                action,
                aggregateType,
                aggregateId,
                correlationId,
                details
        );
    }
}
