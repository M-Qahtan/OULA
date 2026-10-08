package com.oula.integration;

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
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class IntegrationTrustService implements IntegrationIngressPort {
    private static final Duration PRINCIPAL_FRESHNESS = Duration.ofMinutes(5);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private static final Set<String> PARTNER_TYPES = Set.of(
            "GOVERNMENT", "BANK", "INSURER", "REGISTRY", "IDENTITY",
            "SERVICE_PROVIDER", "MAPS", "DATA_PROVIDER", "OTHER"
    );
    private static final Set<String> AUTH_MODES = Set.of(
            "MTLS", "JWS", "OIDC_CLIENT", "SIGNED_WEBHOOK", "OTHER"
    );
    private static final Set<String> DIRECTIONS = Set.of("INBOUND", "OUTBOUND");

    private final IntegrationTrustRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public IntegrationTrustService(
            IntegrationTrustRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public IntegrationPartner registerPartner(
            AccessContext access,
            CreateIntegrationPartnerCommand command,
            UUID correlationId
    ) {
        requireIntegrationOperations(access);
        Objects.requireNonNull(command, "command");

        String partnerCode = canonical(command.partnerCode(), "partnerCode");
        String partnerType = canonical(command.partnerType(), "partnerType");
        String displayName = required(command.displayName(), "displayName");
        String jurisdiction = canonical(command.jurisdiction(), "jurisdiction");
        String authMode = canonical(command.authMode(), "authMode");
        String credentialReference = required(
                command.credentialReference(), "credentialReference"
        );

        if (!PARTNER_TYPES.contains(partnerType)) {
            throw new IllegalArgumentException("unsupported partnerType");
        }
        if (!AUTH_MODES.contains(authMode)) {
            throw new IllegalArgumentException("unsupported authMode");
        }
        if (!command.inboundEnabled() && !command.outboundEnabled()) {
            throw new IllegalArgumentException(
                    "partner must enable inbound or outbound integration"
            );
        }

        IntegrationPartner partner = new IntegrationPartner(
                UuidV7.next(), access.workspaceId(), partnerCode, partnerType,
                displayName, jurisdiction, "ACTIVE", "UNVERIFIED",
                null, null, authMode, credentialReference,
                command.inboundEnabled(), command.outboundEnabled(),
                access.actorId(), clock.instant(), 0
        );
        repository.insertPartner(partner);

        emit(
                access, "INTEGRATION_PARTNER_REGISTERED",
                "integration.partner.registered.v1",
                "IntegrationPartner", partner.id(), correlationId,
                Map.of(
                        "partnerCode", partner.partnerCode(),
                        "partnerType", partner.partnerType(),
                        "authMode", partner.authMode()
                )
        );
        return partner;
    }

    @Transactional
    public IntegrationPartner verifyPartner(
            AccessContext access,
            UUID partnerId,
            UUID evidenceId,
            UUID correlationId
    ) {
        requireIntegrationOperations(access);
        Objects.requireNonNull(evidenceId, "evidenceId");

        IntegrationPartner current = repository.lockPartner(
                access.workspaceId(), partnerId
        );
        IntegrationPartner verified = repository.verifyPartner(
                current, evidenceId, clock.instant()
        );

        emit(
                access, "INTEGRATION_PARTNER_VERIFIED",
                "integration.partner.verified.v1",
                "IntegrationPartner", verified.id(), correlationId,
                Map.of(
                        "partnerCode", verified.partnerCode(),
                        "evidenceId", evidenceId
                )
        );
        return verified;
    }

    @Transactional
    public IntegrationContract createContract(
            AccessContext access,
            UUID partnerId,
            CreateIntegrationContractCommand command,
            UUID correlationId
    ) {
        requireIntegrationOperations(access);
        Objects.requireNonNull(command, "command");

        IntegrationPartner partner = repository.partner(
                access.workspaceId(), partnerId
        );
        if (!"ACTIVE".equals(partner.status())) {
            throw new IllegalStateException("integration partner is not active");
        }

        String direction = canonical(command.direction(), "direction");
        if (!DIRECTIONS.contains(direction)) {
            throw new IllegalArgumentException("unsupported integration direction");
        }
        if ("INBOUND".equals(direction) && !partner.inboundEnabled()) {
            throw new IllegalStateException("partner inbound integration is disabled");
        }
        if ("OUTBOUND".equals(direction) && !partner.outboundEnabled()) {
            throw new IllegalStateException("partner outbound integration is disabled");
        }

        Instant now = clock.instant();
        Instant effectiveFrom = command.effectiveFrom() == null
                ? now : command.effectiveFrom();
        if (command.effectiveUntil() != null
                && !command.effectiveUntil().isAfter(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil must be after effectiveFrom"
            );
        }

        Set<String> dataClasses = canonicalDataClasses(command.allowedDataClasses());
        if (dataClasses.isEmpty()) {
            throw new IllegalArgumentException(
                    "allowedDataClasses must not be empty"
            );
        }

        IntegrationContract contract = new IntegrationContract(
                UuidV7.next(), access.workspaceId(), partner.id(),
                required(command.contractKey(), "contractKey"),
                required(command.version(), "version"),
                direction,
                canonical(command.purpose(), "purpose"),
                canonical(command.operation(), "operation"),
                canonical(command.resourceType(), "resourceType"),
                dataClasses,
                "ACTIVE",
                effectiveFrom,
                command.effectiveUntil(),
                access.actorId(),
                now
        );
        repository.insertContract(contract);

        emit(
                access, "INTEGRATION_CONTRACT_CREATED",
                "integration.contract.created.v1",
                "IntegrationContract", contract.id(), correlationId,
                Map.of(
                        "partnerId", partner.id(),
                        "direction", contract.direction(),
                        "purpose", contract.purpose(),
                        "operation", contract.operation()
                )
        );
        return contract;
    }

    @Override
    @Transactional
    public InboundIntegrationReceipt accept(
            VerifiedExternalPrincipal principal,
            InboundIntegrationCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        validateInboundCommand(command);

        Instant now = clock.instant();
        validatePrincipalFreshness(principal, now);

        IntegrationPartner partner = repository.partner(
                principal.workspaceId(), principal.partnerId()
        );
        validatePrincipalBinding(principal, partner);

        if (!"ACTIVE".equals(partner.status())
                || !"VERIFIED".equals(partner.verificationStatus())
                || !partner.inboundEnabled()) {
            throw new SecurityException(
                    "integration partner is not eligible for inbound traffic"
            );
        }

        String purpose = canonical(command.purpose(), "purpose");
        String operation = canonical(command.operation(), "operation");
        String resourceType = canonical(command.resourceType(), "resourceType");

        IntegrationContract contract = repository.matchingContract(
                principal.workspaceId(), partner.id(), "INBOUND",
                purpose, operation, resourceType, now
        );
        if (contract == null) {
            throw new SecurityException(
                    "no active inbound integration contract matches the message"
            );
        }

        Set<String> requestedDataClasses =
                canonicalDataClasses(command.dataClasses());
        requireDataSubset(requestedDataClasses, contract.allowedDataClasses());

        String externalEventId = required(
                command.externalEventId(), "externalEventId"
        );
        String payloadHash = required(command.payloadHash(), "payloadHash");
        String payloadReference = required(
                command.payloadReference(), "payloadReference"
        );
        Objects.requireNonNull(command.correlationId(), "correlationId");

        InboundIntegrationReceipt existing = repository.inboundReceipt(
                partner.id(), externalEventId
        );
        if (existing != null) {
            if (!existing.workspaceId().equals(principal.workspaceId())
                    || !existing.payloadHash().equals(payloadHash)
                    || !existing.operation().equals(operation)
                    || !existing.resourceType().equals(resourceType)
                    || !existing.purpose().equals(purpose)) {
                throw new IntegrationReplayConflictException(
                        "external event id was replayed with different content or context"
                );
            }
            return existing;
        }

        InboundIntegrationReceipt receipt = new InboundIntegrationReceipt(
                UuidV7.next(), principal.workspaceId(), partner.id(),
                contract.id(), externalEventId, operation, resourceType,
                purpose, requestedDataClasses, payloadHash, payloadReference,
                principal.authMode(), principal.credentialReference(),
                principal.authenticatedAt(), now, command.correlationId(),
                "ACCEPTED"
        );
        repository.insertInbound(receipt);

        Map<String, Object> details = Map.of(
                "partnerId", partner.id(),
                "partnerCode", partner.partnerCode(),
                "contractId", contract.id(),
                "externalEventId", externalEventId,
                "operation", operation,
                "resourceType", resourceType,
                "dataClasses", requestedDataClasses,
                "payloadHash", payloadHash
        );
        audit.append(
                principal.workspaceId(), partner.id(),
                "integration:" + partner.partnerCode(), purpose,
                "INTEGRATION_INBOUND_ACCEPTED", "InboundIntegrationReceipt",
                receipt.id(), command.correlationId(), details
        );
        outbox.append(
                "integration.inbound.accepted.v1",
                "InboundIntegrationReceipt", receipt.id(),
                principal.workspaceId(), command.correlationId(),
                command.correlationId(), details
        );
        return receipt;
    }

    @Transactional
    public OutboundIntegrationRequest prepareOutbound(
            AccessContext access,
            UUID partnerId,
            PrepareOutboundIntegrationCommand command
    ) {
        requireIntegrationOperations(access);
        Objects.requireNonNull(command, "command");
        validateOutboundCommand(command);

        IntegrationPartner partner = repository.partner(
                access.workspaceId(), partnerId
        );
        if (!"ACTIVE".equals(partner.status())
                || !"VERIFIED".equals(partner.verificationStatus())
                || !partner.outboundEnabled()) {
            throw new SecurityException(
                    "integration partner is not eligible for outbound traffic"
            );
        }

        Instant now = clock.instant();
        String purpose = canonical(command.purpose(), "purpose");
        String operation = canonical(command.operation(), "operation");
        String resourceType = canonical(command.resourceType(), "resourceType");

        IntegrationContract contract = repository.matchingContract(
                access.workspaceId(), partner.id(), "OUTBOUND",
                purpose, operation, resourceType, now
        );
        if (contract == null) {
            throw new SecurityException(
                    "no active outbound integration contract matches the request"
            );
        }

        Set<String> requestedDataClasses =
                canonicalDataClasses(command.dataClasses());
        requireDataSubset(requestedDataClasses, contract.allowedDataClasses());

        OutboundIntegrationRequest request = new OutboundIntegrationRequest(
                UuidV7.next(), access.workspaceId(), partner.id(),
                contract.id(), operation, resourceType,
                command.resourceId(), purpose, requestedDataClasses,
                required(command.payloadHash(), "payloadHash"),
                required(command.payloadReference(), "payloadReference"),
                "PREPARED", access.actorId(), now, command.correlationId()
        );
        repository.insertOutbound(request);

        emit(
                access, "INTEGRATION_OUTBOUND_PREPARED",
                "integration.outbound.prepared.v1",
                "OutboundIntegrationRequest", request.id(),
                command.correlationId(),
                Map.of(
                        "partnerId", partner.id(),
                        "contractId", contract.id(),
                        "operation", operation,
                        "resourceType", resourceType,
                        "dataClasses", requestedDataClasses,
                        "payloadHash", request.payloadHash()
                )
        );
        return request;
    }

    private void validatePrincipalBinding(
            VerifiedExternalPrincipal principal,
            IntegrationPartner partner
    ) {
        if (!partner.partnerCode().equals(principal.partnerCode())
                || !partner.authMode().equals(principal.authMode())
                || !partner.credentialReference().equals(
                        principal.credentialReference())) {
            throw new SecurityException(
                    "verified external principal does not match integration partner"
            );
        }
    }

    private void validatePrincipalFreshness(
            VerifiedExternalPrincipal principal,
            Instant now
    ) {
        Objects.requireNonNull(principal.authenticatedAt(), "authenticatedAt");
        if (principal.authenticatedAt().isAfter(now.plus(CLOCK_SKEW))) {
            throw new SecurityException(
                    "external principal authentication time is in the future"
            );
        }
        if (principal.authenticatedAt().isBefore(now.minus(PRINCIPAL_FRESHNESS))) {
            throw new SecurityException(
                    "external principal authentication is stale"
            );
        }
    }

    private void validateInboundCommand(InboundIntegrationCommand command) {
        required(command.externalEventId(), "externalEventId");
        required(command.operation(), "operation");
        required(command.resourceType(), "resourceType");
        required(command.purpose(), "purpose");
        required(command.payloadHash(), "payloadHash");
        required(command.payloadReference(), "payloadReference");
        Objects.requireNonNull(command.correlationId(), "correlationId");
    }

    private void validateOutboundCommand(
            PrepareOutboundIntegrationCommand command
    ) {
        required(command.operation(), "operation");
        required(command.resourceType(), "resourceType");
        Objects.requireNonNull(command.resourceId(), "resourceId");
        required(command.purpose(), "purpose");
        required(command.payloadHash(), "payloadHash");
        required(command.payloadReference(), "payloadReference");
        Objects.requireNonNull(command.correlationId(), "correlationId");
    }

    private void requireDataSubset(
            Set<String> requested,
            Set<String> allowed
    ) {
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("dataClasses must not be empty");
        }
        if (!allowed.containsAll(requested)) {
            Set<String> excess = new LinkedHashSet<>(requested);
            excess.removeAll(allowed);
            throw new SecurityException(
                    "integration data contract does not allow classes: " + excess
            );
        }
    }

    private Set<String> canonicalDataClasses(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> result = new TreeSet<>();
        for (String value : values) {
            result.add(canonical(value, "dataClass"));
        }
        return Collections.unmodifiableSet(result);
    }

    private void requireIntegrationOperations(AccessContext access) {
        Objects.requireNonNull(access, "access");
        if (access.purpose() != AccessPurpose.INTEGRATION_OPERATIONS) {
            throw new SecurityException(
                    "INTEGRATION_OPERATIONS purpose is required"
            );
        }
    }

    private String canonical(String value, String field) {
        return required(value, field).toUpperCase();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
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
}
