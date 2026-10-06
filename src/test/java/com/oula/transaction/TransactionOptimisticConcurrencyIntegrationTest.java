package com.oula.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CI_DB_TEST", matches = "true")
class TransactionOptimisticConcurrencyIntegrationTest {

    @Autowired TransactionApplicationService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void advancesExactlyOnceAndRejectsStaleVersion() {
        UUID workspace = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Test', 'ACTIVE')", workspace);
        jdbc.update("""
                insert into tx.transaction(id, workspace_id, stage, status, version)
                values (?, ?, 'DRAFT', 'OPEN', 0)
                """, transactionId, workspace);

        TransactionSnapshot advanced = service.advance(
                transactionId,
                0,
                TransactionStage.QUALIFIED,
                actorId,
                "qualified for viewing"
        );

        assertThat(advanced.stage()).isEqualTo(TransactionStage.QUALIFIED);
        assertThat(advanced.version()).isEqualTo(1);

        assertThatThrownBy(() -> service.advance(
                transactionId,
                0,
                TransactionStage.VIEWING,
                actorId,
                "stale request"
        )).isInstanceOf(OptimisticConcurrencyException.class);

        Long version = jdbc.queryForObject(
                "select version from tx.transaction where id = ?",
                Long.class,
                transactionId
        );
        Integer history = jdbc.queryForObject(
                "select count(*) from tx.stage_history where transaction_id = ?",
                Integer.class,
                transactionId
        );

        assertThat(version).isEqualTo(1);
        assertThat(history).isEqualTo(1);
    }
}
