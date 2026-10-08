package com.oula.services;

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
import java.util.UUID;

@Service
public class ServiceGraphService {
    private final ServiceGraphRepository repository;
    private final OperationsExecutionService execution;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public ServiceGraphService(
            ServiceGraphRepository repository,
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
    public ServiceProvider registerProvider(
            AccessContext access,
            UUID providerPartyId,
            String displayName,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(providerPartyId, "providerPartyId");
        requireText(displayName, "displayName");

        ServiceProvider provider = new ServiceProvider(
                UuidV7.next(), access.workspaceId(), providerPartyId,
                displayName.trim(), "ACTIVE", "UNVERIFIED",
                null, null, clock.instant(), 0
        );
        repository.insertProvider(provider);
        emit(access, "SERVICE_PROVIDER_REGISTERED", "service_graph.provider.registered.v1",
                "ServiceProvider", provider.id(), correlationId,
                Map.of("providerPartyId", providerPartyId, "verificationStatus", "UNVERIFIED"));
        return provider;
    }

    @Transactional
    public ServiceProvider verifyProvider(
            AccessContext access,
            UUID providerId,
            UUID evidenceId,
            UUID correlationId
    ) {
        requireManagement(access);
        Objects.requireNonNull(evidenceId, "evidenceId");
        ServiceProvider current = repository.lockProvider(access.workspaceId(), providerId);
        ServiceProvider verified = repository.verify(current, evidenceId, clock.instant());
        emit(access, "SERVICE_PROVIDER_VERIFIED", "service_graph.provider.verified.v1",
                "ServiceProvider", verified.id(), correlationId,
                Map.of("providerPartyId", verified.providerPartyId(), "evidenceId", evidenceId));
        return verified;
    }

    @Transactional
    public ProviderCapability addCapability(
            AccessContext access,
            UUID providerId,
            String category,
            UUID correlationId
    ) {
        requireManagement(access);
        String normalized = normalizeCategory(category);
        ServiceProvider provider = repository.lockProvider(access.workspaceId(), providerId);
        if (!"ACTIVE".equals(provider.status())) {
            throw new IllegalStateException("service provider is not active");
        }
        ProviderCapability capability = repository.addCapability(
                UuidV7.next(), provider.id(), normalized, clock.instant()
        );
        emit(access, "SERVICE_PROVIDER_CAPABILITY_SET",
                "service_graph.provider.capability_set.v1",
                "ServiceProvider", provider.id(), correlationId,
                Map.of("category", normalized));
        return capability;
    }

    @Transactional
    public ServiceQuote submitQuote(
            AccessContext access,
            UUID workOrderId,
            UUID providerId,
            BigDecimal amount,
            String currency,
            int leadTimeDays,
            String scopeNote,
            UUID correlationId
    ) {
        requireManagement(access);
        requireMoney(amount, "amount");
        if (leadTimeDays < 0) {
            throw new IllegalArgumentException("leadTimeDays must be non-negative");
        }
        requireText(scopeNote, "scopeNote");

        WorkOrder workOrder = execution.get(access, workOrderId);
        if (!List.of("PENDING_APPROVAL", "APPROVED").contains(workOrder.status())) {
            throw new IllegalStateException("work order is not open for quotes");
        }

        ServiceProvider provider = repository.lockProvider(access.workspaceId(), providerId);
        if (!"ACTIVE".equals(provider.status()) || !"VERIFIED".equals(provider.verificationStatus())) {
            throw new IllegalStateException("provider must be active and verified");
        }
        String category = normalizeCategory(workOrder.category());
        if (!repository.hasActiveCapability(provider.id(), category)) {
            throw new IllegalStateException("provider is not qualified for work order category");
        }

        String normalizedCurrency = normalizeCurrency(currency);
        if (!normalizedCurrency.equals(workOrder.currency())) {
            throw new IllegalArgumentException("quote currency must match work order currency");
        }

        ServiceQuote existing = repository.activeQuote(
                access.workspaceId(), workOrder.id(), provider.id()
        );
        if (existing != null) {
            return existing;
        }

        ServiceQuote quote = new ServiceQuote(
                UuidV7.next(), access.workspaceId(), workOrder.id(),
                provider.id(), provider.providerPartyId(), amount,
                normalizedCurrency, leadTimeDays, scopeNote.trim(),
                "SUBMITTED", clock.instant(), null, null, 0
        );
        repository.insertQuote(quote);
        emit(access, "SERVICE_QUOTE_SUBMITTED", "service_graph.quote.submitted.v1",
                "ServiceQuote", quote.id(), correlationId,
                Map.of("workOrderId", workOrder.id(), "providerId", provider.id(),
                        "amount", amount, "currency", normalizedCurrency,
                        "leadTimeDays", leadTimeDays));
        return quote;
    }

    @Transactional
    public ServiceQuote selectQuote(
            AccessContext access,
            UUID quoteId,
            UUID correlationId
    ) {
        requireManagement(access);
        ServiceQuote quote = repository.lockQuote(access.workspaceId(), quoteId);
        if (!"SUBMITTED".equals(quote.status())) {
            throw new IllegalStateException("quote is not selectable");
        }

        ServiceProvider provider = repository.lockProvider(access.workspaceId(), quote.providerId());
        if (!"ACTIVE".equals(provider.status()) || !"VERIFIED".equals(provider.verificationStatus())) {
            throw new IllegalStateException("provider is no longer eligible");
        }

        WorkOrder workOrder = execution.get(access, quote.workOrderId());
        if ("PENDING_APPROVAL".equals(workOrder.status())) {
            workOrder = execution.approve(access, workOrder.id(), quote.amount(), correlationId);
        } else if ("APPROVED".equals(workOrder.status())) {
            if (workOrder.approvedBudget() == null
                    || quote.amount().compareTo(workOrder.approvedBudget()) > 0) {
                throw new IllegalStateException("selected quote exceeds approved budget");
            }
        } else {
            throw new IllegalStateException("work order cannot accept a quote in current state");
        }

        execution.assign(access, workOrder.id(), quote.providerPartyId(), correlationId);
        ServiceQuote selected = repository.select(quote, access.actorId(), clock.instant());
        emit(access, "SERVICE_QUOTE_SELECTED", "service_graph.quote.selected.v1",
                "ServiceQuote", selected.id(), correlationId,
                Map.of("workOrderId", selected.workOrderId(),
                        "providerId", selected.providerId(),
                        "providerPartyId", selected.providerPartyId(),
                        "amount", selected.amount(), "currency", selected.currency()));
        return selected;
    }

    @Transactional(readOnly = true)
    public List<ServiceQuote> listQuotes(AccessContext access, UUID workOrderId) {
        requireManagement(access);
        execution.get(access, workOrderId);
        return repository.listQuotes(access.workspaceId(), workOrderId);
    }

    @Transactional
    public ProviderOutcome recordOutcome(
            AccessContext access,
            UUID workOrderId,
            Integer rating,
            String outcomeNote,
            UUID correlationId
    ) {
        requireManagement(access);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }

        ProviderOutcome existing = repository.outcomeForWorkOrder(
                access.workspaceId(), workOrderId
        );
        if (existing != null) {
            return existing;
        }

        WorkOrder workOrder = execution.get(access, workOrderId);
        if (!"COMPLETED".equals(workOrder.status()) || workOrder.actualCost() == null) {
            throw new IllegalStateException("provider outcome requires verified completed work");
        }
        ServiceQuote quote = repository.selectedQuote(access.workspaceId(), workOrderId);
        if (!quote.providerPartyId().equals(workOrder.providerPartyId())) {
            throw new IllegalStateException("selected quote provider does not match completed work");
        }

        BigDecimal variance = workOrder.actualCost().subtract(quote.amount());
        ProviderOutcome outcome = new ProviderOutcome(
                UuidV7.next(), access.workspaceId(), workOrderId,
                quote.id(), quote.providerId(), quote.amount(),
                workOrder.actualCost(), variance, rating,
                outcomeNote, access.actorId(), clock.instant()
        );
        repository.insertOutcome(outcome);
        emit(access, "SERVICE_PROVIDER_OUTCOME_RECORDED",
                "service_graph.provider_outcome.recorded.v1",
                "ProviderOutcome", outcome.id(), correlationId,
                Map.of("workOrderId", workOrderId, "providerId", quote.providerId(),
                        "costVariance", variance));
        return outcome;
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, aggregateType,
                aggregateId, correlationId, details);
        outbox.append(eventType, aggregateType, aggregateId, access.workspaceId(),
                correlationId, correlationId, details);
    }

    private void requireManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
        }
    }

    private String normalizeCategory(String category) {
        requireText(category, "category");
        return category.trim().toUpperCase();
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
