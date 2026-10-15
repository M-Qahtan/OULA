package com.oula.transaction;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TransactionRepository {
    private final JdbcClient jdbc;

    TransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<TransactionSnapshot> find(UUID id) {
        return jdbc.sql("""
                select id, workspace_id, stage, version
                  from tx.transaction
                 where id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new TransactionSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        TransactionStage.valueOf(rs.getString("stage")),
                        rs.getLong("version")
                ))
                .optional();
    }

    boolean intentBelongsToWorkspace(UUID intentId, UUID workspaceId) {
        Long count = jdbc.sql("""
                select count(*)
                  from intent.intent
                 where id = :intentId
                   and workspace_id = :workspaceId
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
        return count != null && count == 1L;
    }

    boolean propertyBelongsToWorkspace(UUID propertyId, UUID workspaceId) {
        Long count = jdbc.sql("""
                select count(*)
                  from property.asset
                 where id = :propertyId
                   and workspace_id = :workspaceId
                """)
                .param("propertyId", propertyId)
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
        return count != null && count == 1L;
    }

    boolean hasOpenTransaction(UUID workspaceId, UUID intentId, UUID propertyId) {
        Long count = jdbc.sql("""
                select count(*)
                  from tx.transaction
                 where workspace_id = :workspaceId
                   and intent_id = :intentId
                   and property_id = :propertyId
                   and status = 'OPEN'
                """)
                .param("workspaceId", workspaceId)
                .param("intentId", intentId)
                .param("propertyId", propertyId)
                .query(Long.class)
                .single();
        return count != null && count > 0L;
    }

    void create(UUID id, UUID workspaceId, UUID intentId, UUID propertyId) {
        int inserted = jdbc.sql("""
                insert into tx.transaction
                    (id, workspace_id, intent_id, property_id, stage, status, version)
                values
                    (:id, :workspaceId, :intentId, :propertyId, 'DRAFT', 'OPEN', 0)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("intentId", intentId)
                .param("propertyId", propertyId)
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("transaction could not be created");
        }
    }

    void advance(UUID id, long expectedVersion, TransactionStage target) {
        String status = switch (target) {
            case COMPLETED -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
            default -> "OPEN";
        };
        int updated = jdbc.sql("""
                update tx.transaction
                   set stage = :stage,
                       status = :status,
                       updated_at = now(),
                       version = version + 1
                 where id = :id
                   and version = :expectedVersion
                """)
                .param("stage", target.name())
                .param("status", status)
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();

        if (updated != 1) {
            throw new OptimisticConcurrencyException(id, expectedVersion);
        }
    }

    void recordCreation(
            UUID historyId,
            UUID transactionId,
            UUID actorId,
            String reason
    ) {
        jdbc.sql("""
                insert into tx.stage_history
                    (id, transaction_id, from_stage, to_stage, actor_id, reason)
                values
                    (:id, :transactionId, null, 'DRAFT', :actorId, :reason)
                """)
                .param("id", historyId)
                .param("transactionId", transactionId)
                .param("actorId", actorId)
                .param("reason", reason)
                .update();
    }

    void recordTransition(
            UUID historyId,
            UUID transactionId,
            TransactionStage from,
            TransactionStage to,
            UUID actorId,
            String reason
    ) {
        jdbc.sql("""
                insert into tx.stage_history
                    (id, transaction_id, from_stage, to_stage, actor_id, reason)
                values
                    (:id, :transactionId, :fromStage, :toStage, :actorId, :reason)
                """)
                .param("id", historyId)
                .param("transactionId", transactionId)
                .param("fromStage", from.name())
                .param("toStage", to.name())
                .param("actorId", actorId)
                .param("reason", reason)
                .update();
    }

    void createViewing(
            UUID id,
            UUID transactionId,
            UUID workspaceId,
            Instant preferredAt,
            String note,
            UUID requestedBy
    ) {
        jdbc.sql("""
                insert into tx.viewing_request
                    (id, transaction_id, workspace_id, preferred_at, note, status,
                     requested_by, human_confirmed)
                values
                    (:id, :transactionId, :workspaceId, :preferredAt, :note,
                     'REQUESTED', :requestedBy, true)
                """)
                .param("id", id)
                .param("transactionId", transactionId)
                .param("workspaceId", workspaceId)
                .param("preferredAt", preferredAt)
                .param("note", note)
                .param("requestedBy", requestedBy)
                .update();
    }

    boolean hasViewing(UUID transactionId, UUID workspaceId) {
        Long count = jdbc.sql("""
                select count(*)
                  from tx.viewing_request
                 where transaction_id = :transactionId
                   and workspace_id = :workspaceId
                   and status = 'REQUESTED'
                """)
                .param("transactionId", transactionId)
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
        return count != null && count > 0L;
    }

    List<ViewingRequestSnapshot> viewings(UUID transactionId, UUID workspaceId) {
        return jdbc.sql("""
                select id, transaction_id, workspace_id, preferred_at, note, status,
                       requested_by, created_at
                  from tx.viewing_request
                 where transaction_id = :transactionId
                   and workspace_id = :workspaceId
                 order by created_at asc, id asc
                """)
                .param("transactionId", transactionId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new ViewingRequestSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("transaction_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getTimestamp("preferred_at") == null ? null : rs.getTimestamp("preferred_at").toInstant(),
                        rs.getString("note"),
                        rs.getString("status"),
                        rs.getObject("requested_by", UUID.class),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    void createOffer(
            UUID id,
            UUID transactionId,
            UUID workspaceId,
            BigDecimal amount,
            String currency,
            String termsSummary,
            UUID submittedBy
    ) {
        jdbc.sql("""
                insert into tx.transaction_offer
                    (id, transaction_id, workspace_id, amount, currency, terms_summary,
                     status, submitted_by, human_confirmed)
                values
                    (:id, :transactionId, :workspaceId, :amount, :currency, :termsSummary,
                     'SUBMITTED', :submittedBy, true)
                """)
                .param("id", id)
                .param("transactionId", transactionId)
                .param("workspaceId", workspaceId)
                .param("amount", amount)
                .param("currency", currency)
                .param("termsSummary", termsSummary)
                .param("submittedBy", submittedBy)
                .update();
    }

    List<TransactionOfferSnapshot> offers(UUID transactionId, UUID workspaceId) {
        return jdbc.sql("""
                select id, transaction_id, workspace_id, amount, currency, terms_summary,
                       status, submitted_by, created_at
                  from tx.transaction_offer
                 where transaction_id = :transactionId
                   and workspace_id = :workspaceId
                 order by created_at asc, id asc
                """)
                .param("transactionId", transactionId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new TransactionOfferSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("transaction_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("terms_summary"),
                        rs.getString("status"),
                        rs.getObject("submitted_by", UUID.class),
                        rs.getObject("created_at", Instant.class)
                ))
                .list();
    }
}
