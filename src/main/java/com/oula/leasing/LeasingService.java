package com.oula.leasing;

import com.oula.compliance.ComplianceKernel;
import com.oula.compliance.PolicyRequest;
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
import java.util.Set;
import java.util.UUID;

@Service
public class LeasingService {
    private static final Set<String> LEASE_TYPES =
            Set.of("RESIDENTIAL", "COMMERCIAL", "INDUSTRIAL", "LAND", "OTHER");
    private static final Set<String> PAYMENT_FREQUENCIES =
            Set.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "ONE_TIME");

    private final LeasingRepository repository;
    private final ComplianceKernel compliance;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public LeasingService(
            LeasingRepository repository,
            ComplianceKernel compliance,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.compliance = compliance;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public Lease createDraft(
            AccessContext access,
            UUID propertyId,
            CreateLeaseCommand command,
            UUID correlationId
    ) {
        requireHumanManagement(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.landlordPartyId(), "landlordPartyId");
        Objects.requireNonNull(command.tenantPartyId(), "tenantPartyId");
        Objects.requireNonNull(command.startsAt(), "startsAt");
        Objects.requireNonNull(command.endsAt(), "endsAt");
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new IllegalArgumentException("lease endsAt must be after startsAt");
        }

        String leaseType = normalizeChoice(command.leaseType(), LEASE_TYPES, "leaseType");
        String frequency = normalizeChoice(
                command.paymentFrequency(), PAYMENT_FREQUENCIES, "paymentFrequency"
        );
        BigDecimal rent = requirePositive(command.rentAmount(), "rentAmount");
        BigDecimal deposit = command.securityDeposit();
        if (deposit != null && deposit.signum() < 0) {
            throw new IllegalArgumentException("securityDeposit must be non-negative");
        }
        String currency = normalizeCurrency(command.currency());
        requireText(command.sourceType(), "sourceType");

        Instant now = clock.instant();
        Lease lease = new Lease(
                UuidV7.next(), access.workspaceId(), propertyId,
                command.landlordPartyId(), command.tenantPartyId(),
                leaseType, "DRAFT", command.startsAt(), command.endsAt(),
                rent, currency, frequency, deposit, null,
                trimToNull(command.externalContractReference()),
                command.sourceType().trim().toUpperCase(),
                access.actorId(), now, null, null, null, null, null, 0
        );
        repository.insert(lease);

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("propertyId", propertyId);
        details.put("landlordPartyId", lease.landlordPartyId());
        details.put("tenantPartyId", lease.tenantPartyId());
        details.put("leaseType", lease.leaseType());
        details.put("startsAt", lease.startsAt().toString());
        details.put("endsAt", lease.endsAt().toString());
        details.put("rentAmount", lease.rentAmount());
        details.put("currency", lease.currency());

        emit(access, "LEASE_DRAFT_CREATED", "leasing.lease.draft_created.v1",
                "Lease", lease.id(), correlationId, details);
        return lease;
    }

    @Transactional
    public Lease activate(
            AccessContext access,
            UUID leaseId,
            UUID contractEvidenceId,
            String jurisdiction,
            UUID approvalRequestId,
            UUID correlationId
    ) {
        requireHumanManagement(access);
        Objects.requireNonNull(contractEvidenceId, "contractEvidenceId");
        Lease current = repository.lock(access.workspaceId(), leaseId);
        if (!"DRAFT".equals(current.status())) {
            throw new IllegalStateException("only DRAFT lease can be activated");
        }
        if (!repository.evidenceVerified(access.workspaceId(), contractEvidenceId)) {
            throw new IllegalStateException(
                    "contract evidence must be VERIFIED before lease activation"
            );
        }

        compliance.requireAllowed(
                access,
                new PolicyRequest(
                        "LEASE_ACTIVATE", "Lease", current.id(),
                        jurisdiction, current.rentAmount(), current.currency(),
                        true, true, approvalRequestId,
                        Map.of(
                                "propertyId", current.propertyId(),
                                "amountBasis", "PER_PAYMENT_RENT",
                                "paymentFrequency", current.paymentFrequency()
                        )
                ),
                correlationId
        );

        Instant now = clock.instant();
        Lease activated = repository.activate(
                current, contractEvidenceId, access.actorId(), now
        );
        repository.insertOccupancy(activated, now);

        Map<String, Object> details = Map.of(
                "propertyId", activated.propertyId(),
                "tenantPartyId", activated.tenantPartyId(),
                "contractEvidenceId", contractEvidenceId,
                "startsAt", activated.startsAt().toString(),
                "endsAt", activated.endsAt().toString()
        );
        emit(access, "LEASE_ACTIVATED", "leasing.lease.activated.v1",
                "Lease", activated.id(), correlationId, details);
        return activated;
    }

    @Transactional
    public Lease terminate(
            AccessContext access,
            UUID leaseId,
            String jurisdiction,
            UUID approvalRequestId,
            String reason,
            UUID correlationId
    ) {
        requireHumanManagement(access);
        requireText(reason, "reason");
        Lease current = repository.lock(access.workspaceId(), leaseId);
        if (!"ACTIVE".equals(current.status())) {
            throw new IllegalStateException("only ACTIVE lease can be terminated");
        }

        Instant now = clock.instant();
        if (!now.isAfter(current.startsAt())) {
            throw new IllegalStateException(
                    "future lease must be cancelled rather than terminated"
            );
        }
        if (!now.isBefore(current.endsAt())) {
            throw new IllegalStateException(
                    "ended lease must be expired rather than terminated"
            );
        }

        compliance.requireAllowed(
                access,
                new PolicyRequest(
                        "LEASE_TERMINATE", "Lease", current.id(),
                        jurisdiction, null, null,
                        current.contractEvidenceId() != null,
                        current.contractEvidenceId() != null,
                        approvalRequestId,
                        Map.of("propertyId", current.propertyId())
                ),
                correlationId
        );

        Lease terminated = repository.terminate(
                current, access.actorId(), now, reason.trim()
        );
        repository.closeOccupancy(terminated.id(), now);

        Map<String, Object> details = Map.of(
                "propertyId", terminated.propertyId(),
                "tenantPartyId", terminated.tenantPartyId(),
                "terminatedAt", now.toString(),
                "reason", reason.trim()
        );
        emit(access, "LEASE_TERMINATED", "leasing.lease.terminated.v1",
                "Lease", terminated.id(), correlationId, details);
        return terminated;
    }

    @Transactional(readOnly = true)
    public List<Lease> list(AccessContext access, UUID propertyId) {
        requireHumanManagement(access);
        return repository.list(access.workspaceId(), propertyId);
    }

    @Transactional(readOnly = true)
    public OccupancyPeriod currentOccupancy(
            AccessContext access,
            UUID propertyId
    ) {
        requireHumanManagement(access);
        return repository.currentOccupancy(
                access.workspaceId(), propertyId, clock.instant()
        );
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
        audit.append(
                access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, aggregateType,
                aggregateId, correlationId, details
        );
        outbox.append(
                eventType, aggregateType, aggregateId, access.workspaceId(),
                correlationId, correlationId, details
        );
    }

    private void requireHumanManagement(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.PROPERTY_MANAGEMENT) {
            throw new SecurityException(
                    "PROPERTY_MANAGEMENT human authority is required for leasing"
            );
        }
    }

    private String normalizeChoice(
            String value,
            Set<String> choices,
            String field
    ) {
        requireText(value, field);
        String normalized = value.trim().toUpperCase();
        if (!choices.contains(normalized)) {
            throw new IllegalArgumentException("unsupported " + field);
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        requireText(value, "currency");
        return Currency.getInstance(value.trim().toUpperCase()).getCurrencyCode();
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
