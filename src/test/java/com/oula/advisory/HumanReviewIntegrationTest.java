package com.oula.advisory;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class HumanReviewIntegrationTest {
    @Autowired HumanReviewService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void frozenAdviceHumanDispositionAndVerifiedOutcomeRemainSeparateAppendOnlyFacts() {
        Fixture f=seed();
        HumanReviewCase review=service.capture(f.access(),f.property(),0,
                RentalLifecycleAdvisoryService.RULES_VERSION,
                "VERIFY_UNIT_HANDOVER_RECORD",f.unit(),null,UUID.randomUUID());
        assertThat(review.actionCode()).isEqualTo("VERIFY_UNIT_HANDOVER_RECORD");
        assertThat(review.rulesVersion()).isEqualTo("rental-lifecycle-advisory-v1");
        assertThat(review.sourceMetricsJson()).contains("recordedOccupancy");

        assertThatThrownBy(()->service.observeOutcome(f.access(),review.id(),
                "IMPROVEMENT_OBSERVED","Premature claim",UUID.randomUUID(),UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must accept");

        HumanReviewEvent deferred=service.decide(f.access(),review.id(),"DEFER",
                "Request a handover inspection first",UUID.randomUUID());
        assertThat(deferred.sequenceNo()).isEqualTo(1);
        HumanReviewEvent accepted=service.decide(f.access(),review.id(),"ACCEPT_FOR_REVIEW",
                "Human manager will inspect the unit",UUID.randomUUID());
        assertThat(accepted.sequenceNo()).isEqualTo(2);

        UUID evidence=verifiedEvidence(f.workspace(),"ADVISORY_OUTCOME");
        HumanReviewEvent outcome=service.observeOutcome(f.access(),review.id(),
                "INCONCLUSIVE","Document does not establish improvement",evidence,UUID.randomUUID());
        assertThat(outcome.sequenceNo()).isEqualTo(3);
        assertThat(outcome.eventType()).isEqualTo("OUTCOME_OBSERVATION");
        HumanReviewTimeline timeline=service.timeline(f.access(),review.id());
        assertThat(timeline.history()).hasSize(3);
        assertThat(timeline.reviewCase().sourceFingerprint()).isEqualTo(review.sourceFingerprint());

        assertThatThrownBy(()->service.decide(f.access(),review.id(),"DECLINE",
                "Trying to overwrite a recorded outcome",UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        assertThat(jdbc.queryForObject("""
                select count(*) from platform.outbox_event
                 where workspace_id=? and event_type like 'advisory.review.%'
                """,Integer.class,f.workspace())).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select count(*) from platform.audit_log
                 where workspace_id=? and action like 'ADVISORY_HUMAN_%'
                """,Integer.class,f.workspace())).isEqualTo(4);

        AccessContext otherWorkspace=new AccessContext(f.actor(),"other",UUID.randomUUID(),
                AccessPurpose.PROPERTY_MANAGEMENT);
        assertThatThrownBy(()->service.timeline(otherWorkspace,review.id()))
                .isInstanceOf(NoSuchElementException.class);

        // Do this last: trigger rejection aborts a PostgreSQL transaction.
        assertThatThrownBy(()->jdbc.update(
                "delete from advisory.review_event where id=?",deferred.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownOrWrongWorkspaceOutcomeEvidenceCanNeverBeAccepted() {
        Fixture f=seed();
        HumanReviewCase review=service.capture(f.access(),f.property(),0,
                RentalLifecycleAdvisoryService.RULES_VERSION,
                "VERIFY_UNIT_HANDOVER_RECORD",f.unit(),null,UUID.randomUUID());
        service.decide(f.access(),review.id(),"ACCEPT_FOR_REVIEW",
                "Review source evidence",UUID.randomUUID());
        UUID foreign=verifiedEvidence(UUID.randomUUID(),"ADVISORY_OUTCOME");
        assertThatThrownBy(()->service.observeOutcome(f.access(),review.id(),
                "IMPROVEMENT_OBSERVED","Wrong workspace",foreign,UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(service.timeline(f.access(),review.id()).history()).hasSize(1);
    }

    private Fixture seed() {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),u=UUID.randomUUID(),actor=UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC);
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Human feedback","ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",3,1_200_000);
        jdbc.update("""
                insert into tenancy.unit
                  (id,workspace_id,property_id,unit_code,status,created_by,created_at)
                values (?,?,?,?,'ACTIVE',?,?)
                """,u,w,p,"B-101",actor,now);
        return new Fixture(w,p,u,actor,
                new AccessContext(actor,"review-test",w,AccessPurpose.PROPERTY_MANAGEMENT));
    }

    private UUID verifiedEvidence(UUID w,String type) {
        UUID id=UUID.randomUUID();
        OffsetDateTime at=OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC);
        jdbc.update("""
                insert into docs.evidence
                  (id,workspace_id,evidence_type,source,verification_status,content_hash,captured_at)
                values (?,?,?,'HUMAN_REVIEW','VERIFIED',?,?)
                """,id,w,type,UUID.randomUUID().toString(),at);
        return id;
    }

    record Fixture(UUID workspace,UUID property,UUID unit,UUID actor,AccessContext access){}
}
