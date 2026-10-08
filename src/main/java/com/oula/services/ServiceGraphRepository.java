package com.oula.services;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class ServiceGraphRepository {
    private final JdbcClient jdbc;

    ServiceGraphRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertProvider(ServiceProvider provider) {
        jdbc.sql("""
                insert into service_graph.provider
                    (id, workspace_id, provider_party_id, display_name, status,
                     verification_status, created_at, version)
                values
                    (:id, :workspaceId, :partyId, :displayName, :status,
                     :verificationStatus, :createdAt, :version)
                """)
                .param("id", provider.id())
                .param("workspaceId", provider.workspaceId())
                .param("partyId", provider.providerPartyId())
                .param("displayName", provider.displayName())
                .param("status", provider.status())
                .param("verificationStatus", provider.verificationStatus())
                .param("createdAt", utc(provider.createdAt()))
                .param("version", provider.version())
                .update();
    }

    ServiceProvider lockProvider(UUID workspaceId, UUID providerId) {
        return jdbc.sql("""
                select *
                  from service_graph.provider
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", providerId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapProvider(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("service provider not found"));
    }

    ServiceProvider verify(ServiceProvider current, UUID evidenceId, Instant verifiedAt) {
        int updated = jdbc.sql("""
                update service_graph.provider
                   set verification_status = 'VERIFIED',
                       verification_evidence_id = :evidenceId,
                       verified_at = :verifiedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                """)
                .param("evidenceId", evidenceId)
                .param("verifiedAt", utc(verifiedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update();
        requireUpdated(updated);
        return new ServiceProvider(
                current.id(), current.workspaceId(), current.providerPartyId(),
                current.displayName(), current.status(), "VERIFIED", evidenceId,
                verifiedAt, current.createdAt(), current.version() + 1
        );
    }

    ProviderCapability addCapability(UUID id, UUID providerId, String category, Instant createdAt) {
        jdbc.sql("""
                insert into service_graph.provider_capability
                    (id, provider_id, category, status, created_at)
                values (:id, :providerId, :category, 'ACTIVE', :createdAt)
                on conflict (provider_id, category)
                do update set status = 'ACTIVE'
                """)
                .param("id", id)
                .param("providerId", providerId)
                .param("category", category)
                .param("createdAt", utc(createdAt))
                .update();

        return jdbc.sql("""
                select id, provider_id, category, status, created_at
                  from service_graph.provider_capability
                 where provider_id = :providerId
                   and category = :category
                """)
                .param("providerId", providerId)
                .param("category", category)
                .query((rs, rowNum) -> new ProviderCapability(
                        rs.getObject("id", UUID.class),
                        rs.getObject("provider_id", UUID.class),
                        rs.getString("category"),
                        rs.getString("status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class))
                ))
                .single();
    }

    boolean hasActiveCapability(UUID providerId, String category) {
        Integer count = jdbc.sql("""
                select count(*)
                  from service_graph.provider_capability
                 where provider_id = :providerId
                   and category = :category
                   and status = 'ACTIVE'
                """)
                .param("providerId", providerId)
                .param("category", category)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    void insertQuote(ServiceQuote quote) {
        jdbc.sql("""
                insert into service_graph.quote
                    (id, workspace_id, work_order_id, provider_id, provider_party_id,
                     amount, currency, lead_time_days, scope_note, status,
                     submitted_at, version)
                values
                    (:id, :workspaceId, :workOrderId, :providerId, :providerPartyId,
                     :amount, :currency, :leadTimeDays, :scopeNote, 'SUBMITTED',
                     :submittedAt, :version)
                """)
                .param("id", quote.id())
                .param("workspaceId", quote.workspaceId())
                .param("workOrderId", quote.workOrderId())
                .param("providerId", quote.providerId())
                .param("providerPartyId", quote.providerPartyId())
                .param("amount", quote.amount())
                .param("currency", quote.currency())
                .param("leadTimeDays", quote.leadTimeDays())
                .param("scopeNote", quote.scopeNote())
                .param("submittedAt", utc(quote.submittedAt()))
                .param("version", quote.version())
                .update();
    }

    ServiceQuote activeQuote(UUID workspaceId, UUID workOrderId, UUID providerId) {
        return jdbc.sql("""
                select *
                  from service_graph.quote
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                   and provider_id = :providerId
                   and status in ('SUBMITTED','SELECTED')
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .param("providerId", providerId)
                .query((rs, rowNum) -> mapQuote(rs))
                .optional()
                .orElse(null);
    }

    ServiceQuote lockQuote(UUID workspaceId, UUID quoteId) {
        return jdbc.sql("""
                select *
                  from service_graph.quote
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", quoteId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapQuote(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("service quote not found"));
    }

    ServiceQuote selectedQuote(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from service_graph.quote
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                   and status = 'SELECTED'
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> mapQuote(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("selected service quote not found"));
    }

    List<ServiceQuote> listQuotes(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from service_graph.quote
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                 order by amount, lead_time_days, submitted_at
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> mapQuote(rs))
                .list();
    }

    ServiceQuote select(ServiceQuote quote, UUID actorId, Instant selectedAt) {
        int updated = jdbc.sql("""
                update service_graph.quote
                   set status = 'SELECTED',
                       selected_by = :actorId,
                       selected_at = :selectedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'SUBMITTED'
                """)
                .param("actorId", actorId)
                .param("selectedAt", utc(selectedAt))
                .param("id", quote.id())
                .param("version", quote.version())
                .update();
        requireUpdated(updated);

        jdbc.sql("""
                update service_graph.quote
                   set status = 'REJECTED',
                       version = version + 1
                 where work_order_id = :workOrderId
                   and id <> :selectedId
                   and status = 'SUBMITTED'
                """)
                .param("workOrderId", quote.workOrderId())
                .param("selectedId", quote.id())
                .update();

        return new ServiceQuote(
                quote.id(), quote.workspaceId(), quote.workOrderId(),
                quote.providerId(), quote.providerPartyId(), quote.amount(),
                quote.currency(), quote.leadTimeDays(), quote.scopeNote(),
                "SELECTED", quote.submittedAt(), actorId, selectedAt,
                quote.version() + 1
        );
    }

    ProviderOutcome outcomeForWorkOrder(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from service_graph.provider_outcome
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> mapOutcome(rs))
                .optional()
                .orElse(null);
    }

    void insertOutcome(ProviderOutcome outcome) {
        jdbc.sql("""
                insert into service_graph.provider_outcome
                    (id, workspace_id, work_order_id, quote_id, provider_id,
                     quoted_amount, actual_cost, cost_variance, rating,
                     outcome_note, recorded_by, recorded_at)
                values
                    (:id, :workspaceId, :workOrderId, :quoteId, :providerId,
                     :quotedAmount, :actualCost, :costVariance, :rating,
                     :outcomeNote, :recordedBy, :recordedAt)
                """)
                .param("id", outcome.id())
                .param("workspaceId", outcome.workspaceId())
                .param("workOrderId", outcome.workOrderId())
                .param("quoteId", outcome.quoteId())
                .param("providerId", outcome.providerId())
                .param("quotedAmount", outcome.quotedAmount())
                .param("actualCost", outcome.actualCost())
                .param("costVariance", outcome.costVariance())
                .param("rating", outcome.rating())
                .param("outcomeNote", outcome.outcomeNote())
                .param("recordedBy", outcome.recordedBy())
                .param("recordedAt", utc(outcome.recordedAt()))
                .update();
    }

    private ServiceProvider mapProvider(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ServiceProvider(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("provider_party_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("verification_status"),
                rs.getObject("verification_evidence_id", UUID.class),
                instant(rs.getObject("verified_at", OffsetDateTime.class)),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private ServiceQuote mapQuote(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ServiceQuote(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("work_order_id", UUID.class),
                rs.getObject("provider_id", UUID.class),
                rs.getObject("provider_party_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getInt("lead_time_days"),
                rs.getString("scope_note"),
                rs.getString("status"),
                instant(rs.getObject("submitted_at", OffsetDateTime.class)),
                rs.getObject("selected_by", UUID.class),
                instant(rs.getObject("selected_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private ProviderOutcome mapOutcome(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProviderOutcome(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("work_order_id", UUID.class),
                rs.getObject("quote_id", UUID.class),
                rs.getObject("provider_id", UUID.class),
                rs.getBigDecimal("quoted_amount"),
                rs.getBigDecimal("actual_cost"),
                rs.getBigDecimal("cost_variance"),
                (Integer) rs.getObject("rating"),
                rs.getString("outcome_note"),
                rs.getObject("recorded_by", UUID.class),
                instant(rs.getObject("recorded_at", OffsetDateTime.class))
        );
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("concurrent Service Graph state change");
        }
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
