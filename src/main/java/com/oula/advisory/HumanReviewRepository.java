package com.oula.advisory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class HumanReviewRepository {
    private final JdbcClient jdbc;

    HumanReviewRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void insertCase(HumanReviewCase c) {
        jdbc.sql("""
                insert into advisory.review_case (
                  id,workspace_id,property_id,unit_id,lease_id,rules_version,
                  dimension,action_code,initial_priority,source_generated_at,
                  source_metrics,source_fingerprint,captured_by,captured_at
                ) values (
                  :id,:w,:property,:unit,:lease,:rules,:dimension,:action,:priority,
                  :generated,cast(:metrics as jsonb),:fingerprint,:actor,:at
                )
                """)
                .param("id",c.id()).param("w",c.workspaceId())
                .param("property",c.propertyId()).param("unit",c.unitId())
                .param("lease",c.leaseId()).param("rules",c.rulesVersion())
                .param("dimension",c.dimension()).param("action",c.actionCode())
                .param("priority",c.initialPriority()).param("generated",utc(c.sourceGeneratedAt()))
                .param("metrics",c.sourceMetricsJson()).param("fingerprint",c.sourceFingerprint())
                .param("actor",c.capturedBy()).param("at",utc(c.capturedAt()))
                .update();
    }

    HumanReviewCase load(UUID workspaceId, UUID caseId, boolean lock) {
        return jdbc.sql("""
                select * from advisory.review_case
                 where workspace_id=:w and id=:id
                """ + (lock ? " for update" : ""))
                .param("w",workspaceId).param("id",caseId)
                .query((rs,row)->new HumanReviewCase(
                        rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                        rs.getObject("property_id",UUID.class),rs.getObject("unit_id",UUID.class),
                        rs.getObject("lease_id",UUID.class),rs.getString("rules_version"),
                        rs.getString("dimension"),rs.getString("action_code"),
                        rs.getString("initial_priority"),
                        instant(rs.getObject("source_generated_at",OffsetDateTime.class)),
                        rs.getString("source_metrics"),rs.getString("source_fingerprint"),
                        rs.getObject("captured_by",UUID.class),
                        instant(rs.getObject("captured_at",OffsetDateTime.class))))
                .optional().orElseThrow(()->new NoSuchElementException("review case not found"));
    }

    List<HumanReviewEvent> history(UUID workspaceId, UUID caseId) {
        return jdbc.sql("""
                select * from advisory.review_event
                 where workspace_id=:w and review_case_id=:id
                 order by sequence_no
                """)
                .param("w",workspaceId).param("id",caseId)
                .query((rs,row)->mapEvent(rs)).list();
    }

    void insertEvent(HumanReviewEvent event) {
        jdbc.sql("""
                insert into advisory.review_event (
                  id,workspace_id,review_case_id,sequence_no,event_type,value_code,
                  rationale,evidence_id,actor_id,recorded_at
                ) values (
                  :id,:w,:review,:sequence,:type,:value,:rationale,:evidence,:actor,:at
                )
                """)
                .param("id",event.id()).param("w",event.workspaceId())
                .param("review",event.reviewCaseId()).param("sequence",event.sequenceNo())
                .param("type",event.eventType()).param("value",event.valueCode())
                .param("rationale",event.rationale()).param("evidence",event.evidenceId())
                .param("actor",event.actorId()).param("at",utc(event.recordedAt()))
                .update();
    }


    HumanFeedbackSummary summary(UUID workspaceId, UUID propertyId) {
        jdbc.sql("select id from property.asset where workspace_id=:w and id=:p")
                .param("w",workspaceId).param("p",propertyId)
                .query(UUID.class).optional()
                .orElseThrow(()->new NoSuchElementException("property not found in workspace"));
        return jdbc.sql("""
                select count(*) as captured,
                       count(*) filter (where coalesce(flags.decided,false)) as decided,
                       count(*) filter (where coalesce(flags.observed,false)) as observed,
                       count(*) filter (where coalesce(flags.improved,false)) as improved,
                       count(*) filter (where coalesce(flags.inconclusive,false)) as inconclusive
                  from advisory.review_case c
                  left join lateral (
                      select bool_or(e.event_type='DECISION') as decided,
                             bool_or(e.event_type='OUTCOME_OBSERVATION') as observed,
                             bool_or(e.value_code='IMPROVEMENT_OBSERVED') as improved,
                             bool_or(e.value_code='INCONCLUSIVE') as inconclusive
                        from advisory.review_event e
                       where e.workspace_id=c.workspace_id and e.review_case_id=c.id
                  ) flags on true
                 where c.workspace_id=:w and c.property_id=:p
                """)
                .param("w",workspaceId).param("p",propertyId)
                .query((rs,row)->new HumanFeedbackSummary(
                        propertyId,
                        rs.getLong("captured"),rs.getLong("decided"),rs.getLong("observed"),
                        rs.getLong("improved"),rs.getLong("inconclusive"),
                        "HUMAN_RECORDED_DOCUMENTARY_OBSERVATIONS_NOT_CAUSAL_EFFECT"))
                .single();
    }

    private HumanReviewEvent mapEvent(ResultSet rs) throws SQLException {
        return new HumanReviewEvent(
                rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                rs.getObject("review_case_id",UUID.class),rs.getInt("sequence_no"),
                rs.getString("event_type"),rs.getString("value_code"),
                rs.getString("rationale"),rs.getObject("evidence_id",UUID.class),
                rs.getObject("actor_id",UUID.class),
                instant(rs.getObject("recorded_at",OffsetDateTime.class)));
    }

    private OffsetDateTime utc(Instant t) { return OffsetDateTime.ofInstant(t, ZoneOffset.UTC); }
    private Instant instant(OffsetDateTime t) { return t == null ? null : t.toInstant(); }
}
