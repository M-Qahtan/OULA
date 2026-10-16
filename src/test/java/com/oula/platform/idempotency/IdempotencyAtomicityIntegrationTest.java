package com.oula.platform.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration gate. Intentionally NOT @Transactional:
 * an outer test transaction would hide a missing service transaction boundary.
 */
@SpringBootTest
class IdempotencyAtomicityIntegrationTest {
    @Autowired IdempotencyService idempotency;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean IdempotencyRepository repository;

    @Test
    void injectedFailureAfterBusinessWriteRollsBackBothWriteAndIdempotencyClaim() {
        UUID workspaceId = UUID.randomUUID();
        UUID createdByAction = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String operation = "test.atomic-commit.v1";
        String hash = "test-fingerprint-v1";

        jdbc.update(
                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Atomicity Test','ACTIVE')",
                workspaceId);
        try {
            assertThatThrownBy(() -> idempotency.execute(
                    workspaceId, key, operation, hash, 201, String.class,
                    () -> {
                        jdbc.update(
                                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Rolled-back Side Effect','ACTIVE')",
                                createdByAction);
                        throw new IllegalStateException("injected-after-business-write");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("injected-after-business-write");

            // A committed business row with a missing replay receipt is unacceptable.
            assertThat(rowCount("select count(*) from iam.workspace where id = ?", createdByAction))
                    .isZero();
            assertThat(rowCount(
                    "select count(*) from platform.idempotency_record where workspace_id = ? and idempotency_key = ?",
                    workspaceId, key)).isZero();

            var success = idempotency.execute(
                    workspaceId, key, operation, hash, 201, String.class,
                    () -> {
                        jdbc.update(
                                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Committed Side Effect','ACTIVE')",
                                createdByAction);
                        return createdByAction.toString();
                    });
            assertThat(success.replayed()).isFalse();

            var replay = idempotency.execute(
                    workspaceId, key, operation, hash, 201, String.class,
                    () -> { throw new AssertionError("replay must not execute business mutation"); });
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.value()).isEqualTo(createdByAction.toString());
            assertThat(rowCount("select count(*) from iam.workspace where id = ?", createdByAction))
                    .isEqualTo(1L);
        } finally {
            jdbc.update("delete from platform.idempotency_record where workspace_id = ?", workspaceId);
            jdbc.update("delete from iam.workspace where id = ?", createdByAction);
            jdbc.update("delete from iam.workspace where id = ?", workspaceId);
        }
    }

    @Test
    void sqlConstraintFailurePreservesOriginalErrorAndRollsBackClaim() {
        UUID workspaceId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        jdbc.update(
                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Constraint Test','ACTIVE')",
                workspaceId);
        try {
            assertThatThrownBy(() -> idempotency.execute(
                    workspaceId, key, "test.constraint.v1", "constraint-hash", 201, String.class,
                    () -> {
                        // Duplicate primary key aborts the PostgreSQL transaction (SQLSTATE 23505).
                        jdbc.update(
                                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Duplicate','ACTIVE')",
                                workspaceId);
                        return "unreachable";
                    }))
                    .satisfies(error -> {
                        Throwable root = error;
                        while (root.getCause() != null) root = root.getCause();
                        assertThat(root.getMessage()).contains("duplicate key");
                    });
            assertThat(rowCount(
                    "select count(*) from platform.idempotency_record where workspace_id = ? and idempotency_key = ?",
                    workspaceId, key)).isZero();
        } finally {
            jdbc.update("delete from platform.idempotency_record where workspace_id = ?", workspaceId);
            jdbc.update("delete from iam.workspace where id = ?", workspaceId);
        }
    }

    @Test
    void failedReceiptCompletionAfterSuccessfulCallbackRollsBackBusinessWriteAndClaim() {
        UUID workspaceId = UUID.randomUUID();
        UUID sideEffectId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        jdbc.update(
                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Receipt Failure Test','ACTIVE')",
                workspaceId);
        try {
            doThrow(new IllegalStateException("injected-receipt-complete-failure"))
                    .when(repository).complete(eq(workspaceId), eq(key), eq(201), anyString());
            assertThatThrownBy(() -> idempotency.execute(
                    workspaceId, key, "test.receipt.v1", "receipt-hash", 201, String.class,
                    () -> {
                        jdbc.update(
                                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Must Roll Back','ACTIVE')",
                                sideEffectId);
                        return sideEffectId.toString();
                    }))
                    .satisfies(error -> {
                        // Spring's @Repository exception translation may wrap the injected
                        // repository failure; assert its true cause, not the proxy wrapper.
                        Throwable root = error;
                        while (root.getCause() != null) root = root.getCause();
                        assertThat(root)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("injected-receipt-complete-failure");
                    });
            assertThat(rowCount("select count(*) from iam.workspace where id = ?", sideEffectId)).isZero();
            assertThat(rowCount(
                    "select count(*) from platform.idempotency_record where workspace_id = ? and idempotency_key = ?",
                    workspaceId, key)).isZero();
        } finally {
            jdbc.update("delete from platform.idempotency_record where workspace_id = ?", workspaceId);
            jdbc.update("delete from iam.workspace where id = ?", sideEffectId);
            jdbc.update("delete from iam.workspace where id = ?", workspaceId);
        }
    }

    @Test
    void concurrentIdenticalKeyProducesOneCommittedMutationAndAReplay() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID sideEffectId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String operation = "test.atomic-concurrency.v1";
        String hash = "same-request-hash";
        AtomicInteger executed = new AtomicInteger();
        CountDownLatch insideFirstMutation = new CountDownLatch(1);
        CountDownLatch releaseFirstMutation = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        jdbc.update(
                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Concurrency Test','ACTIVE')",
                workspaceId);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> idempotency.execute(
                    workspaceId, key, operation, hash, 201, String.class,
                    () -> {
                        executed.incrementAndGet();
                        jdbc.update(
                                "insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Single Mutation','ACTIVE')",
                                sideEffectId);
                        insideFirstMutation.countDown();
                        try {
                            if (!releaseFirstMutation.await(15, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("concurrent test release timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("concurrent test interrupted", e);
                        }
                        return sideEffectId.toString();
                    }));
            assertThat(insideFirstMutation.await(15, TimeUnit.SECONDS)).isTrue();

            var second = pool.submit(() -> {
                secondEntered.countDown();
                return idempotency.execute(
                    workspaceId, key, operation, hash, 201, String.class,
                    () -> {
                        executed.incrementAndGet();
                        throw new AssertionError("duplicate mutation is not allowed");
                    });
            });
            assertThat(secondEntered.await(15, TimeUnit.SECONDS)).isTrue();
            // Observe an actual PostgreSQL lock wait, not merely two scheduled Java tasks.
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline) {
                Long waiting = jdbc.queryForObject("""
                        select count(*) from pg_stat_activity
                         where datname = current_database()
                           and wait_event_type = 'Lock'
                           and query like '%idempotency_record%'
                        """, Long.class);
                if (waiting != null && waiting > 0) { blocked = true; break; }
                Thread.sleep(50);
            }
            assertThat(blocked).as("second request must block on first uncommitted claim").isTrue();
            releaseFirstMutation.countDown();

            var committed = first.get(20, TimeUnit.SECONDS);
            var replay = second.get(20, TimeUnit.SECONDS);
            assertThat(committed.replayed()).isFalse();
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.value()).isEqualTo(sideEffectId.toString());
            assertThat(executed.get()).isEqualTo(1);
            assertThat(rowCount("select count(*) from iam.workspace where id = ?", sideEffectId))
                    .isEqualTo(1L);

            assertThatThrownBy(() -> idempotency.execute(
                    workspaceId, key, operation, "different-request-hash", 201, String.class,
                    () -> { throw new AssertionError("different hash must fail before mutation"); }))
                    .isInstanceOf(IdempotencyConflictException.class);
        } finally {
            releaseFirstMutation.countDown();
            pool.shutdownNow();
            jdbc.update("delete from platform.idempotency_record where workspace_id = ?", workspaceId);
            jdbc.update("delete from iam.workspace where id = ?", sideEffectId);
            jdbc.update("delete from iam.workspace where id = ?", workspaceId);
        }
    }

    private Long rowCount(String sql, Object... params) {
        return jdbc.queryForObject(sql, Long.class, params);
    }
}
