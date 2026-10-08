package com.oula.operations;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OperationsExecutionService {
    private final OperationsExecutionRepository repository;
    private final PropertyManagementRepository managementRepository;
    private final PropertyManagementService management;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public OperationsExecutionService(
            OperationsExecutionRepository repository,
            PropertyManagementRepository managementRepository,
            PropertyManagementService management,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.managementRepository = managementRepository;
        this.management = management;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public WorkOrder createFromAction(
            AccessContext access,
            UUID actionId,
            CreateWorkOrderCommand command,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(correlationId, "correlationId");
        requireText(command.category(), "category");
        requireText(command.title(), "title");
        requireText(command.scopeDescription(), "scopeDescription");
        requireMoney(command.estimatedCost(), "estimatedCost");
        String currency = normalizeCurrency(command.currency());

        ActionItem action = managementRepository.lockAction(access.workspaceId(), actionId);
        if ("COMPLETED".equals(action.status())) {
            throw new IllegalStateException("completed action cannot create a work order");
        }

        WorkOrder existing = repository.activeForAction(access.workspaceId(), actionId);
        if (existing != null) {
            return existing;
        }

        Instant now = clock.instant();
        WorkOrder workOrder = new WorkOrder(
                UuidV7.next(),
                access.workspaceId(),
                action.propertyId(),
                action.id(),
                command.category(),
                command.title(),
                command.scopeDescription(),
                "PENDING_APPROVAL",
                null,
                command.estimatedCost(),
                null,
                null,
                currency,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                access.actorId(),
                now,
                0
        );
        repository.insert(workOrder);
        emit(access, "OPERATIONS_WORK_ORDER_CREATED", "operations.work_order.created.v1",
                workOrder, correlationId, Map.of(
                        "propertyId", workOrder.propertyId(),
                        "actionItemId", workOrder.actionItemId(),
                        "estimatedCost", workOrder.estimatedCost(),
                        "currency", workOrder.currency()
                ));
        return workOrder;
    }

    @Transactional
    public WorkOrder approve(
            AccessContext access,
            UUID workOrderId,
            BigDecimal approvedBudget,
            UUID correlationId
    ) {
        requireManagement(access);
        requireMoney(approvedBudget, "approvedBudget");
        WorkOrder current = repository.lock(access.workspaceId(), workOrderId);
        if (!"PENDING_APPROVAL".equals(current.status())) {
            throw new IllegalStateException("work order is not awaiting approval");
        }
        WorkOrder updated = repository.approve(current, access.actorId(), approvedBudget, clock.instant());
        emit(access, "OPERATIONS_WORK_ORDER_APPROVED", "operations.work_order.approved.v1",
                updated, correlationId, Map.of("approvedBudget", approvedBudget, "currency", updated.currency()));
        return updated;
    }

    @Transactional
    public WorkOrder assign(
            AccessContext access,
            UUID workOrderId,
            UUID providerPartyId,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(providerPartyId, "providerPartyId");
        WorkOrder current = repository.lock(access.workspaceId(), workOrderId);
        if (!"APPROVED".equals(current.status())) {
            throw new IllegalStateException("work order must be approved before assignment");
        }
        WorkOrder updated = repository.assign(current, providerPartyId);
        emit(access, "OPERATIONS_WORK_ORDER_ASSIGNED", "operations.work_order.assigned.v1",
                updated, correlationId, Map.of("providerPartyId", providerPartyId));
        return updated;
    }

    @Transactional
    public WorkOrder start(AccessContext access, UUID workOrderId, UUID correlationId) {
        requireManagement(access);
        WorkOrder current = repository.lock(access.workspaceId(), workOrderId);
        if (!"ASSIGNED".equals(current.status())) {
            throw new IllegalStateException("work order must be assigned before start");
        }
        WorkOrder updated = repository.start(current, clock.instant());
        emit(access, "OPERATIONS_WORK_ORDER_STARTED", "operations.work_order.started.v1",
                updated, correlationId, Map.of("providerPartyId", updated.providerPartyId()));
        return updated;
    }

    @Transactional
    public WorkOrder submitCompletion(
            AccessContext access,
            UUID workOrderId,
            SubmitWorkOrderCompletionCommand command,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(command, "command");
        requireMoney(command.actualCost(), "actualCost");
        Objects.requireNonNull(command.completionEvidenceId(), "completionEvidenceId");
        requireText(command.completionNote(), "completionNote");

        WorkOrder current = repository.lock(access.workspaceId(), workOrderId);
        if (!"IN_PROGRESS".equals(current.status())) {
            throw new IllegalStateException("work order must be in progress");
        }
        if (current.approvedBudget() != null
                && command.actualCost().compareTo(current.approvedBudget()) > 0) {
            throw new IllegalStateException("actual cost exceeds approved budget");
        }

        WorkOrder updated = repository.submitCompletion(
                current,
                command.actualCost(),
                command.completionEvidenceId(),
                command.completionNote(),
                clock.instant()
        );
        emit(access, "OPERATIONS_WORK_ORDER_COMPLETION_SUBMITTED",
                "operations.work_order.completion_submitted.v1",
                updated, correlationId, Map.of(
                        "actualCost", updated.actualCost(),
                        "currency", updated.currency(),
                        "completionEvidenceId", updated.completionEvidenceId()
                ));
        return updated;
    }

    @Transactional
    public WorkOrder verifyCompletion(
            AccessContext access,
            UUID workOrderId,
            UUID correlationId
    ) {
        requireManagement(access);
        WorkOrder current = repository.lock(access.workspaceId(), workOrderId);
        if (!"COMPLETION_REVIEW".equals(current.status())) {
            throw new IllegalStateException("work order is not awaiting completion verification");
        }

        WorkOrder completed = repository.complete(current, clock.instant());
        management.completeAction(
                access,
                completed.actionItemId(),
                "Verified work order " + completed.id() + ": " + completed.completionNote(),
                correlationId
        );
        emit(access, "OPERATIONS_WORK_ORDER_COMPLETED", "operations.work_order.completed.v1",
                completed, correlationId, Map.of(
                        "actualCost", completed.actualCost(),
                        "currency", completed.currency(),
                        "completionEvidenceId", completed.completionEvidenceId()
                ));
        return completed;
    }

    @Transactional(readOnly = true)
    public WorkOrder get(AccessContext access, UUID workOrderId) {
        requireManagement(access);
        Objects.requireNonNull(workOrderId, "workOrderId");
        return repository.get(access.workspaceId(), workOrderId);
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> list(AccessContext access, UUID propertyId) {
        requireManagement(access);
        management.overview(access, propertyId);
        return repository.list(access.workspaceId(), propertyId);
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            WorkOrder workOrder,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                auditAction,
                "WorkOrder",
                workOrder.id(),
                correlationId,
                details
        );
        outbox.append(
                eventType,
                "WorkOrder",
                workOrder.id(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
    }

    private void requireManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
    }

    private void requireMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private String normalizeCurrency(String currency) {
        requireText(currency, "currency");
        return Currency.getInstance(currency.trim().toUpperCase()).getCurrencyCode();
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
