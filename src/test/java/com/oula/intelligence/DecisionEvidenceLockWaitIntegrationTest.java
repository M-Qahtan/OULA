package com.oula.intelligence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DecisionEvidenceLockWaitIntegrationTest {
    @Autowired EvidenceRegistry registry;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest @ValueSource(booleans={true,false})
    void evidenceExpiringOrRejectedDuringActualLockWaitIsRejected(boolean expiry) throws Exception {
        UUID ws=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Lock test','ACTIVE')",ws);
        registry.register(id,ws,"INSPECTION","FICTIONAL_INSPECTION","fictional-source",null,
                "sha256:test","VERIFIED",Instant.now().minusSeconds(60),expiry ? Instant.now().plusSeconds(2) : null,"SA");
        var executor=Executors.newSingleThreadExecutor();
        try (var blocker=dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            int pid;
            try(var statement=blocker.createStatement();var result=statement.executeQuery("select pg_backend_pid()")) {
                result.next();pid=result.getInt(1);
            }
            try(var lock=blocker.prepareStatement("select id from intelligence.evidence where id=? for update")) {
                lock.setObject(1,id);
                try(var result=lock.executeQuery()){assertThat(result.next()).isTrue();}
            }
            var admission=executor.submit(()-> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(s->registry.requireDecisionEligible(ws,List.of(id)));
                    return (Throwable)null;
                } catch(Throwable failure){return failure;}
            });
            try {
                long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
                boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=Boolean.TRUE.equals(jdbc.queryForObject(
                            "select exists(select 1 from pg_stat_activity where ?=any(pg_blocking_pids(pid)))",Boolean.class,pid));
                    if(waiting)break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("must observe a real evidence row lock wait").isTrue();
                // Either expire without changing the tuple, or revoke while the
                // guard waits. Both must be denied after the writer releases.
                if(expiry) {
                    try(var statement=blocker.createStatement()){statement.execute("select pg_sleep(2.1)");}
                } else {
                    try(var update=blocker.prepareStatement("update intelligence.evidence set verification_status='REJECTED' where id=?")) {
                        update.setObject(1,id);update.executeUpdate();
                    }
                }
                blocker.commit();
                assertThat(admission.get(10,TimeUnit.SECONDS)).isInstanceOf(NoSuchElementException.class);
            } finally {
                blocker.rollback();executor.shutdown();
                assertThat(executor.awaitTermination(15,TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            executor.shutdownNow();
            jdbc.update("delete from intelligence.evidence where id=?",id);
            jdbc.update("delete from iam.workspace where id=?",ws);
        }
    }
}
