package com.oula.property;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyApplicationService {
    private static final Set<String> MVP_ASSET_TYPES = Set.of("RESIDENTIAL");
    private static final Set<String> VISIBILITY = Set.of("PUBLIC", "WORKSPACE", "RESTRICTED");
    private static final Set<TruthStatus> EXTERNAL_FACT_STATES =
            Set.of(TruthStatus.DECLARED, TruthStatus.OBSERVED);

    private final PropertyRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public PropertyApplicationService(
            PropertyRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public PropertyAssetView createAsset(
            AccessContext access,
            String assetType,
            String district,
            Integer bedrooms,
            UUID correlationId
    ) {
        requireAccess(access);
        if (!MVP_ASSET_TYPES.contains(assetType)) {
            throw new IllegalArgumentException("Riyadh MVP supports RESIDENTIAL assets");
        }
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("district is required");
        }
        if (bedrooms == null || bedrooms < 0) {
            throw new IllegalArgumentException("bedrooms must be non-negative");
        }
        UUID propertyId = UuidV7.next();
        repository.insertAsset(
                propertyId, access.workspaceId(), assetType, district.trim(), bedrooms
        );
        emit(
                access, "PROPERTY_ASSET_CREATED", "property.asset.created.v1",
                "Property", propertyId, correlationId,
                Map.of("assetType", assetType, "district", district.trim())
        );
        return repository.findOwned(access.workspaceId(), propertyId);
    }

    @Transactional
    public PropertyFactView recordFact(
            AccessContext access,
            UUID propertyId,
            RecordPropertyFactCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");
        if (command.factKey() == null || command.factKey().isBlank()) {
            throw new IllegalArgumentException("factKey is required");
        }
        if (command.value() == null || command.value().isEmpty()) {
            throw new IllegalArgumentException("fact value is required");
        }
        if (!EXTERNAL_FACT_STATES.contains(command.truthStatus())) {
            throw new IllegalArgumentException(
                    "external property facts may only be DECLARED or OBSERVED"
            );
        }
        if (command.sourceType() == null || command.sourceType().isBlank()) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (!VISIBILITY.contains(command.visibility())) {
            throw new IllegalArgumentException("invalid fact visibility");
        }
        if (command.confidence() != null
                && (command.confidence() < 0.0 || command.confidence() > 1.0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (command.truthStatus() == TruthStatus.OBSERVED
                && command.evidenceReference() == null) {
            throw new IllegalArgumentException("OBSERVED fact requires evidence reference");
        }

        UUID factId = UuidV7.next();
        repository.insertFact(
                factId, access.workspaceId(), propertyId, access.actorId(), command
        );
        emit(
                access, "PROPERTY_FACT_RECORDED", "property.fact.recorded.v1",
                "PropertyFact", factId, correlationId,
                Map.of(
                        "propertyId", propertyId,
                        "truthStatus", command.truthStatus().name(),
                        "visibility", command.visibility()
                )
        );
        return repository.findFact(access.workspaceId(), propertyId, factId);
    }

    @Transactional
    public PropertyFactView verifyFact(
            AccessContext access,
            UUID propertyId,
            UUID factId,
            long expectedVersion,
            UUID evidenceReference,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(evidenceReference, "evidenceReference");
        repository.verifyFact(
                access.workspaceId(),
                propertyId,
                factId,
                expectedVersion,
                evidenceReference,
                access.actorId(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        emit(
                access, "PROPERTY_FACT_VERIFIED", "property.fact.verified.v1",
                "PropertyFact", factId, correlationId,
                Map.of("propertyId", propertyId, "evidenceReference", evidenceReference)
        );
        return repository.findFact(access.workspaceId(), propertyId, factId);
    }

    @Transactional(readOnly = true)
    public PropertyAssetView get(AccessContext access, UUID propertyId) {
        requireAccess(access);
        return repository.findOwned(access.workspaceId(), propertyId);
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
                access.purpose().name(), auditAction, aggregateType, aggregateId,
                correlationId, details
        );
        outbox.append(
                eventType, aggregateType, aggregateId, access.workspaceId(),
                correlationId, correlationId, details
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
    }
}
