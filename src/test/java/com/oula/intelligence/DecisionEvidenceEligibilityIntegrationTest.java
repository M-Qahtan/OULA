package com.oula.intelligence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DecisionEvidenceEligibilityIntegrationTest {
    @Autowired EvidenceRegistry registry;
    @Autowired JdbcTemplate jdbc;

    private UUID workspace() {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Evidence test','ACTIVE')",id);
        return id;
    }
    private UUID evidence(UUID workspace) {
        UUID id=UUID.randomUUID();
        registry.register(id,workspace,"INSPECTION","FICTIONAL_INSPECTION","fictional-source",null,
                "sha256:test","VERIFIED",Instant.now().minusSeconds(60),null,"SA");
        return id;
    }
    @Test void onlyCurrentVerifiedDocumentedEvidenceIsDecisionEligible() {
        UUID ws=workspace(); UUID id=evidence(ws);
        assertThatCode(()->registry.requireDecisionEligible(ws,List.of(id))).doesNotThrowAnyException();
        for (String status:List.of("UNVERIFIED","REJECTED","EXPIRED","UNKNOWN")) {
            jdbc.update("update intelligence.evidence set verification_status=? where id=?",status,id);
            assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id))).isInstanceOf(NoSuchElementException.class);
        }
    }
    @Test void rejectsFutureExpiredMissingAndForeignEvidenceAndMixedSets() {
        UUID ws=workspace(); UUID id=evidence(ws); UUID foreign=evidence(workspace());
        assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of())).isInstanceOf(IllegalArgumentException.class);
        for(UUID bad:List.of(foreign,UUID.randomUUID()))
            assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id,bad))).isInstanceOf(NoSuchElementException.class);
        jdbc.update("update intelligence.evidence set valid_from=clock_timestamp()+interval '1 hour' where id=?",id);
        assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id))).isInstanceOf(NoSuchElementException.class);
        jdbc.update("update intelligence.evidence set valid_from=clock_timestamp()-interval '1 hour',valid_until=clock_timestamp() where id=?",id);
        assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id))).isInstanceOf(NoSuchElementException.class);
    }
    @Test void wallClockExpiryRejectsEvidenceInLongRunningTransaction() {
        UUID ws=workspace(); UUID id=evidence(ws);
        jdbc.update("update intelligence.evidence set valid_until=clock_timestamp()+interval '100 milliseconds' where id=?",id);
        jdbc.execute("select pg_sleep(0.2)");
        assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id))).isInstanceOf(NoSuchElementException.class);
    }
    @Test void rejectsUndocumentedSourcesWithoutChangingDocumentaryObservationPolicy() {
        UUID ws=workspace(); UUID id=evidence(ws);
        jdbc.update("update intelligence.evidence set source_identity=null,content_reference=null where id=?",id);
        assertThatThrownBy(()->registry.requireDecisionEligible(ws,List.of(id))).isInstanceOf(NoSuchElementException.class);
        jdbc.update("update intelligence.evidence set verification_status='UNVERIFIED' where id=?",id);
        assertThatCode(()->registry.requireAvailable(ws,List.of(id))).doesNotThrowAnyException();
    }
}
