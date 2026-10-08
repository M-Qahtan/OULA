package com.oula.settlement;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.OperationsExecutionService;
import com.oula.operations.WorkOrder;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SettlementService {
    private static final Set<String> STATUSES =
            Set.of("PENDING", "SETTLED", "FAILED", "REVERSED");

    private final SettlementRepository repository;
    private final OperationsExecutionService execution;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public SettlementService(
            SettlementRepository repository,
            OperationsExecutionService execution,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.execution = execution;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public SettlementReference record(
            AccessContext access,
            UUID workOrderId,
            String processorCode,
            String externalReference,
            BigDecimal amount,
            String currency,
            String status,
            UUID evidenceId,
            UUID correlationId
    ) {
        requireManagement(access);
        requireText(processorCode, "processorCode");
        requireText(externalReference, "externalReference");
        requireMoney(amount, "amount");
        String normalizedCurrency = normalizeCurrency(currency);
        String normalizedStatus = normalizeStatus(status);
        if ("SETTLED".equals(normalizedStatus) && evidenceId == null) {
            throw new IllegalArgumentException("settled reference requires evidence");
        }

        WorkOrder workOrder = execution.get(access, workOrderId);
        if (!"COMPLETED".equals(workOrder.status())
                || workOrder.providerPartyId() == null
                || workOrder.actualCost() == null) {
            throw new IllegalStateException("settlement reference requires verified completed work");
        }
        if (!normalizedCurrency.equals(workOrder.currency())) {
            throw new IllegalArgumentException("settlement currency must match work order currency");
        }

        String processor = processorCode.trim().toUpperCase();
        String external = externalReference.trim();
        SettlementReference existing = repository.findByExternalReference(processor, external);
        if (existing != null) {
            if (!existing.workspaceId().equals(access.workspaceId())
                    || !existing.workOrderId().equals(workOrderId)
                    || existing.amount().compareTo(amount) != 0
                    || !existing.status().equals(normalizedStatus)) {
                throw new IllegalStateException("external settlement reference is already bound differently");
            }
            return existing;
        }

        if ("SETTLED".equals(normalizedStatus)) {
            BigDecimal total = repository.settledAmount(access.workspaceId(), workOrderId)
                    .add(amount);
            if (total.compareTo(workOrder.actualCost()) > 0) {
                throw new IllegalStateException("settled amount exceeds verified actual cost");
            }
        }

        SettlementReference reference = new SettlementReference(
                UuidV7.next(), access.workspaceId(), workOrderId,
                workOrder.providerPartyId(), processor, external,
                amount, normalizedCurrency, normalizedStatus, evidenceId,
                access.actorId(), clock.instant()
        );
        repository.insert(reference);

        Map<String, Object> details = Map.of(
                "workOrderId", workOrderId,
                "providerPartyId", workOrder.providerPartyId(),
                "processorCode", processor,
                "amount", amount,
                "currency", normalizedCurrency,
                "status", normalizedStatus
        );
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), "SETTLEMENT_REFERENCE_RECORDED",
                "SettlementReference", reference.id(), correlationId, details);
        outbox.append("settlement.reference.recorded.v1", "SettlementReference",
                reference.id(), access.workspaceId(), correlationId,
                correlationId, details);
        return reference;
    }

    @Transactional(readOnly = true)
    public List<SettlementReference> list(AccessContext access, UUID workOrderId) {
        requireManagement(access);
        execution.get(access, workOrderId);
        return repository.list(access.workspaceId(), workOrderId);
    }

    private void requireManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
    }

    private String normalizeStatus(String status) {
        requireText(status, "status");
        String normalized = status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported settlement status");
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        requireText(currency, "currency");
        return Currency.getInstance(currency.trim().toUpperCase()).getCurrencyCode();
    }

    private void requireMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
