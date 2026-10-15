package com.oula.iam;

import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConsentService {
    private final JdbcClient jdbc;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ConsentService(JdbcClient jdbc, AuditWriter audit, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ConsentEventView record(
            AccessContext access,
            ConsentCode consentCode,
            ConsentDecision decision,
            String reason,
            UUID eventId,
            UUID correlationId
    ) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(consentCode, "consentCode");
        Objects.requireNonNull(decision, "decision");

        jdbc.sql("""
                insert into iam.consent_event
                  (id, actor_id, workspace_id, purpose, consent_code, decision,
                   reason, source, correlation_id)
                values
                  (:id, :actorId, :workspaceId, :purpose, :consentCode, :decision,
                   :reason, 'USER_EXPLICIT', :correlationId)
                """)
                .param("id", eventId)
                .param("actorId", access.actorId())
                .param("workspaceId", access.workspaceId())
                .param("purpose", access.purpose().name())
                .param("consentCode", consentCode.name())
                .param("decision", decision.name())
                .param("reason", reason)
                .param("correlationId", correlationId)
                .update();

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "iam.consent." + decision.name().toLowerCase(),
                "Consent",
                eventId,
                correlationId,
                Map.of(
                        "consentCode", consentCode.name(),
                        "decision", decision.name(),
                        "source", "USER_EXPLICIT"
                )
        );

        outbox.append(
                "iam.consent.changed.v1",
                "Consent",
                eventId,
                access.workspaceId(),
                correlationId,
                correlationId,
                Map.of(
                        "actorId", access.actorId(),
                        "purpose", access.purpose().name(),
                        "consentCode", consentCode.name(),
                        "decision", decision.name()
                )
        );

        return latest(access, consentCode);
    }

    @Transactional(readOnly = true)
    public List<ConsentEventView> current(AccessContext access) {
        return jdbc.sql("""
                select distinct on (consent_code)
                       consent_code, decision, reason, source, occurred_at
                  from iam.consent_event
                 where actor_id = :actorId
                   and workspace_id = :workspaceId
                   and purpose = :purpose
                 order by consent_code, occurred_at desc, id desc
                """)
                .param("actorId", access.actorId())
                .param("workspaceId", access.workspaceId())
                .param("purpose", access.purpose().name())
                .query((rs, rowNum) -> new ConsentEventView(
                        ConsentCode.valueOf(rs.getString("consent_code")),
                        ConsentDecision.valueOf(rs.getString("decision")),
                        rs.getString("reason"),
                        rs.getString("source"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }

    private ConsentEventView latest(AccessContext access, ConsentCode consentCode) {
        return jdbc.sql("""
                select consent_code, decision, reason, source, occurred_at
                  from iam.consent_event
                 where actor_id = :actorId
                   and workspace_id = :workspaceId
                   and purpose = :purpose
                   and consent_code = :consentCode
                 order by occurred_at desc, id desc
                 limit 1
                """)
                .param("actorId", access.actorId())
                .param("workspaceId", access.workspaceId())
                .param("purpose", access.purpose().name())
                .param("consentCode", consentCode.name())
                .query((rs, rowNum) -> new ConsentEventView(
                        ConsentCode.valueOf(rs.getString("consent_code")),
                        ConsentDecision.valueOf(rs.getString("decision")),
                        rs.getString("reason"),
                        rs.getString("source"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()
                ))
                .single();
    }
}
