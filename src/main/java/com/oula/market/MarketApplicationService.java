package com.oula.market;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import com.oula.property.PropertyAccessQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketApplicationService {
    private static final Set<String> TRANSACTION_TYPES = Set.of("SALE", "RENT");

    private final ListingRepository repository;
    private final PropertyAccessQuery properties;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public MarketApplicationService(
            ListingRepository repository,
            PropertyAccessQuery properties,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.properties = properties;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ListingView createDraft(
            AccessContext access,
            UUID propertyId,
            String transactionType,
            BigDecimal askingPrice,
            String currency,
            UUID correlationId
    ) {
        requireAccess(access);
        properties.requireOwned(access.workspaceId(), propertyId);
        if (!TRANSACTION_TYPES.contains(transactionType)) {
            throw new IllegalArgumentException("transactionType must be SALE or RENT");
        }
        if (askingPrice == null || askingPrice.signum() <= 0) {
            throw new IllegalArgumentException("askingPrice must be positive");
        }
        if (!"SAR".equals(currency)) {
            throw new IllegalArgumentException("Riyadh MVP currently requires SAR");
        }

        UUID listingId = UuidV7.next();
        repository.insert(
                listingId,
                access.workspaceId(),
                propertyId,
                transactionType,
                askingPrice,
                currency,
                access.actorId()
        );
        emit(
                access, "MARKET_LISTING_CREATED", "market.listing.created.v1",
                listingId, correlationId,
                Map.of(
                        "propertyId", propertyId,
                        "transactionType", transactionType,
                        "currency", currency
                )
        );
        return repository.findOwned(access.workspaceId(), listingId);
    }

    @Transactional
    public ListingView publish(
            AccessContext access,
            UUID listingId,
            long expectedVersion,
            UUID correlationId
    ) {
        requireAccess(access);
        ListingView current = repository.findOwned(access.workspaceId(), listingId);
        properties.requireOwned(access.workspaceId(), current.propertyId());
        repository.publish(access.workspaceId(), listingId, expectedVersion);
        emit(
                access, "MARKET_LISTING_PUBLISHED", "market.listing.published.v1",
                listingId, correlationId,
                Map.of("propertyId", current.propertyId(), "expectedVersion", expectedVersion)
        );
        return repository.findOwned(access.workspaceId(), listingId);
    }

    @Transactional
    public ListingView withdraw(
            AccessContext access,
            UUID listingId,
            long expectedVersion,
            UUID correlationId
    ) {
        requireAccess(access);
        ListingView current = repository.findOwned(access.workspaceId(), listingId);
        repository.withdraw(access.workspaceId(), listingId, expectedVersion);
        emit(
                access, "MARKET_LISTING_WITHDRAWN", "market.listing.withdrawn.v1",
                listingId, correlationId,
                Map.of("propertyId", current.propertyId(), "expectedVersion", expectedVersion)
        );
        return repository.findOwned(access.workspaceId(), listingId);
    }

    @Transactional(readOnly = true)
    public ListingView get(AccessContext access, UUID listingId) {
        requireAccess(access);
        return repository.findOwned(access.workspaceId(), listingId);
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            UUID listingId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, "Listing", listingId,
                correlationId, details
        );
        outbox.append(
                eventType, "Listing", listingId, access.workspaceId(),
                correlationId, correlationId, details
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
    }
}
