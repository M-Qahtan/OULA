package com.oula.api;

import com.oula.iam.OfficeMembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real committed transactions: a sequential test cannot expose a stale lock-wait snapshot. */
@SpringBootTest
class OfficeInvitationAcceptanceConcurrencyIntegrationTest {
    @Autowired OfficeMembershipService members;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    private final UUID workspace = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID invitation = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final OfficeMembershipService.RecipientIdentity recipient =
            new OfficeMembershipService.RecipientIdentity(actor, "https://issuer.test.oula", "recipient-" + actor);

    @AfterEach
    void cleanup() {
        jdbc.update("delete from platform.outbox_event where workspace_id=?", workspace);
        jdbc.update("delete from platform.audit_log where workspace_id=?", workspace);
        jdbc.update("delete from iam.institutional_membership where workspace_id=?", workspace);
        jdbc.update("delete from iam.institutional_invitation_cancellation where workspace_id=?", workspace);
        jdbc.update("delete from iam.institutional_member_invitation where workspace_id=?", workspace);
        jdbc.update("delete from iam.workspace where id=?", workspace);
    }

    @Test
    void cancellationCommittedWhileAcceptanceWaitsMustVetoAdmission() throws Exception {
        seed();
        // Models the controller's successful pre-idempotency authorization.
        members.requireInvitationForRecipient(workspace, invitation, recipient);
        var executor = Executors.newSingleThreadExecutor();
        try (Connection cancelling = dataSource.getConnection()) {
            cancelling.setAutoCommit(false);
            int blocker;
            try (var statement = cancelling.createStatement();
                 var result = statement.executeQuery("select pg_backend_pid()")) {
                result.next();
                blocker = result.getInt(1);
            }
            try (var lock = cancelling.prepareStatement(
                    "select id from iam.institutional_member_invitation where id=? for update")) {
                lock.setObject(1, invitation);
                try (var result = lock.executeQuery()) { assertThat(result.next()).isTrue(); }
            }
            var acceptance = executor.submit(() -> {
                try {
                    members.accept(workspace, invitation, recipient, UUID.randomUUID());
                    return (Throwable) null;
                } catch (Throwable failure) { return failure; }
            });
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject(
                            "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                            Boolean.class, blocker));
                    if (waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("acceptance must really wait on the cancellation transaction").isTrue();
                try (var cancel = cancelling.prepareStatement("""
                        insert into iam.institutional_invitation_cancellation
                          (invitation_id,workspace_id,cancelled_by) values (?,?,?)
                        """)) {
                    cancel.setObject(1, invitation);
                    cancel.setObject(2, workspace);
                    cancel.setObject(3, admin);
                    cancel.executeUpdate();
                }
                cancelling.commit();
                assertThat(acceptance.get(10, TimeUnit.SECONDS)).isInstanceOf(AccessDeniedException.class);
            } finally {
                cancelling.rollback();
                executor.shutdown();
                assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            }
        } finally { executor.shutdownNow(); }
        assertNoAdmission();
    }

    @Test
    void expiryUsesWallClockEvenWhenAcceptanceTransactionStartedEarlier() {
        seed();
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    update iam.institutional_member_invitation
                       set expires_at=clock_timestamp()+interval '100 milliseconds' where id=?
                    """, invitation);
            jdbc.execute("select pg_sleep(0.2)");
            members.accept(workspace, invitation, recipient, UUID.randomUUID());
        })).isInstanceOf(AccessDeniedException.class);
        assertNoAdmission();
    }

    @Test
    void disabledOfficeCannotBeAdmittedAfterSuccessfulPrecheck() {
        seed();
        members.requireInvitationForRecipient(workspace, invitation, recipient);
        jdbc.update("update iam.workspace set status='INACTIVE' where id=?", workspace);
        assertThatThrownBy(() -> members.accept(workspace, invitation, recipient, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertNoAdmission();
    }

    private void seed() {
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'ORGANIZATION','Concurrency test','ACTIVE')", workspace);
        jdbc.update("""
                insert into iam.institutional_member_invitation
                  (id,workspace_id,actor_id,issuer,subject,member_role,status,invited_by,expires_at)
                values (?,?,?,?,?,'STAFF','PENDING',?,clock_timestamp()+interval '1 hour')
                """, invitation, workspace, actor, recipient.issuer(), recipient.subject(), admin);
    }

    private void assertNoAdmission() {
        assertThat(jdbc.queryForObject("select count(*) from iam.institutional_membership where workspace_id=?", Long.class, workspace)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform.audit_log where workspace_id=?", Long.class, workspace)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform.outbox_event where workspace_id=?", Long.class, workspace)).isZero();
        assertThat(jdbc.queryForObject("select status from iam.institutional_member_invitation where id=?", String.class, invitation)).isEqualTo("PENDING");
    }
}
