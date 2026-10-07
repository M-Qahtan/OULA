package com.oula.market;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class ListingRepository {
    private final JdbcClient jdbc;

    ListingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(
            UUID listingId,
            UUID workspaceId,
            UUID propertyId,
            String transactionType,
            BigDecimal askingPrice,
            String currency,
            UUID createdBy
    ) {
        jdbc.sql("""
                insert into market.listing
                    (id, workspace_id, property_id, transaction_type, status,
                     asking_price, currency, created_by, version)
                values
                    (:id, :workspaceId, :propertyId, :transactionType, 'DRAFT',
                     :askingPrice, :currency, :createdBy, 0)
                """)
                .param("id", listingId)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("transactionType", transactionType)
                .param("askingPrice", askingPrice)
                .param("currency", currency)
                .param("createdBy", createdBy)
                .update();
    }

    ListingView findOwned(UUID workspaceId, UUID listingId) {
        return jdbc.sql("""
                select id, workspace_id, property_id, transaction_type, status,
                       asking_price, currency, published_at, withdrawn_at, version
                  from market.listing
                 where id = :listingId
                   and workspace_id = :workspaceId
                """)
                .param("listingId", listingId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("listing not found"));
    }

    void publish(UUID workspaceId, UUID listingId, long expectedVersion) {
        int updated = jdbc.sql("""
                update market.listing
                   set status = 'PUBLISHED',
                       published_at = now(),
                       withdrawn_at = null,
                       updated_at = now(),
                       version = version + 1
                 where id = :listingId
                   and workspace_id = :workspaceId
                   and status = 'DRAFT'
                   and version = :expectedVersion
                   and asking_price is not null
                   and asking_price > 0
                """)
                .param("listingId", listingId)
                .param("workspaceId", workspaceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("listing cannot be published from current state/version");
        }
    }

    void withdraw(UUID workspaceId, UUID listingId, long expectedVersion) {
        int updated = jdbc.sql("""
                update market.listing
                   set status = 'WITHDRAWN',
                       withdrawn_at = now(),
                       updated_at = now(),
                       version = version + 1
                 where id = :listingId
                   and workspace_id = :workspaceId
                   and status = 'PUBLISHED'
                   and version = :expectedVersion
                """)
                .param("listingId", listingId)
                .param("workspaceId", workspaceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("listing cannot be withdrawn from current state/version");
        }
    }

    private ListingView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime published = rs.getObject("published_at", OffsetDateTime.class);
        OffsetDateTime withdrawn = rs.getObject("withdrawn_at", OffsetDateTime.class);
        return new ListingView(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getString("transaction_type"),
                rs.getString("status"),
                rs.getBigDecimal("asking_price"),
                rs.getString("currency"),
                published == null ? null : published.toInstant(),
                withdrawn == null ? null : withdrawn.toInstant(),
                rs.getLong("version")
        );
    }
}
