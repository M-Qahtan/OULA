package com.oula.transaction;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    Optional<TransactionSnapshot> findByOriginDecision(
            UUID workspaceId,
            UUID originDecisionId
    ) {
        return jdbc.sql("""
                select id, workspace_id, stage, version
                  from tx.transaction
                 where workspace_id = :workspaceId
                   and origin_decision_id = :originDecisionId
                """)
                .param("workspaceId", workspaceId)
                .param("originDecisionId", originDecisionId)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    boolean open(
            UUID id,
            UUID workspaceId,
            UUID intentId,
            UUID propertyId,
            UUID originDecisionId,
            UUID openedBy
    ) {
        int inserted = jdbc.sql("""
                insert into tx.transaction
                    (id, workspace_id, intent_id, property_id, stage, status,
                     origin_decision_id, opened_by, version)
                values
                    (:id, :workspaceId, :intentId, :propertyId, 'DRAFT', 'OPEN',
                     :originDecisionId, :openedBy, 0)
                on conflict (workspace_id, origin_decision_id)
                    where origin_decision_id is not null
                do nothing
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("intentId", intentId)
                .param("propertyId", propertyId)
                .param("originDecisionId", originDecisionId)
                .param("openedBy", openedBy)
                .update();
        return inserted == 1;
    }

    void advance(UUID id, long expectedVersion, TransactionStage target) {
        int updated = jdbc.sql("""
                update tx.transaction
                   set stage = :stage,
                       updated_at = now(),
                       version = version + 1
                 where id = :id
                   and version = :expectedVersion
                """)
                .param("stage", target.name())
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();

        if (updated != 1) {
            throw new OptimisticConcurrencyException(id, expectedVersion);
        }
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

    private TransactionSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TransactionSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                TransactionStage.valueOf(rs.getString("stage")),
                rs.getLong("version")
        );
    }
}
