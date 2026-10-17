package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
class EvidenceRegistry {
    private final JdbcClient jdbc;

    EvidenceRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void register(
            UUID evidenceId,
            UUID workspaceId,
            String evidenceType,
            String sourceType,
            String sourceIdentity,
            String contentReference,
            String contentHash,
            String verificationStatus,
            Instant capturedAt,
            Instant validUntil,
            String jurisdiction
    ) {
        jdbc.sql("""
                insert into intelligence.evidence
                    (id, workspace_id, subject_type, subject_id, evidence_type,
                     source_type, source_identity, captured_at, content_reference,
                     content_hash, verification_status, valid_from, valid_until,
                     sensitivity, jurisdiction, metadata)
                values
                    (:id, :workspaceId, 'WORKSPACE', :workspaceId, :evidenceType,
                     :sourceType, :sourceIdentity, :capturedAt, :contentReference,
                     :contentHash, :verificationStatus, :capturedAt, :validUntil,
                     'INTERNAL', :jurisdiction, '{}'::jsonb)
                """)
                .param("id", evidenceId)
                .param("workspaceId", workspaceId)
                .param("evidenceType", evidenceType)
                .param("sourceType", sourceType)
                .param("sourceIdentity", sourceIdentity)
                .param("capturedAt", utc(capturedAt))
                .param("contentReference", contentReference)
                .param("contentHash", contentHash)
                .param("verificationStatus", verificationStatus)
                .param("validUntil", utc(validUntil))
                .param("jurisdiction", jurisdiction)
                .update();
    }

    void requireAvailable(UUID workspaceId, List<UUID> evidenceIds) {
        for (UUID evidenceId : evidenceIds) {
            Integer count = jdbc.sql("""
                    select count(*)
                      from intelligence.evidence
                     where id = :id
                       and workspace_id = :workspaceId
                       and verification_status not in ('REJECTED', 'EXPIRED')
                       and (valid_until is null or valid_until > now())
                    """)
                    .param("id", evidenceId)
                    .param("workspaceId", workspaceId)
                    .query(Integer.class)
                    .single();

            if (count == null || count != 1) {
                throw new NoSuchElementException(
                        "evidence not available in workspace: " + evidenceId
                );
            }
        }
    }

    /** Material decision support is stricter than a documentary observation.
     * Caller must hold a transaction; lock all rows before wall-clock rechecks.
     */
    void requireDecisionEligible(UUID workspaceId, List<UUID> evidenceIds) {
        if (evidenceIds.isEmpty())
            throw new IllegalArgumentException("material decision requires evidence");
        var ordered = evidenceIds.stream().distinct().sorted().toList();
        for (UUID id : ordered) {
            jdbc.sql("""
                    select id from intelligence.evidence
                     where id=:id and workspace_id=:ws for share
                    """).param("id", id).param("ws", workspaceId).query(UUID.class).optional()
                    .orElseThrow(() -> new NoSuchElementException("decision evidence unavailable"));
        }
        // Lock attestation and issuer after evidence in sorted UUID order.
        // Revocation cannot race past a material-decision admission.
        for (UUID id : ordered) {
            jdbc.sql("""
                    select a.id from intelligence.evidence e
                      join intelligence.evidence_attestation t
                        on t.evidence_id=e.id and t.workspace_id=e.workspace_id
                      join intelligence.source_authority a
                        on a.id=t.authority_id and a.workspace_id=e.workspace_id
                     where e.id=:id and e.workspace_id=:ws
                     for share of a,t
                    """).param("id",id).param("ws",workspaceId)
                    .query(UUID.class).optional()
                    .orElseThrow(()->new NoSuchElementException("source authority unavailable"));
        }
        // A lock wait may cross expiry without changing the locked tuple.
        // Inspect in a fresh statement after every required lock has been acquired.
        for (UUID id : ordered) {
            jdbc.sql("""
                    select id from intelligence.evidence
                     where id=:id and workspace_id=:ws
                       and verification_status='VERIFIED'
                       and captured_at<=clock_timestamp()
                       and valid_from<=clock_timestamp()
                       and (valid_until is null or valid_until>clock_timestamp())
                       and nullif(btrim(source_type),'') is not null
                       and nullif(btrim(content_hash),'') is not null
                       and (nullif(btrim(source_identity),'') is not null
                            or nullif(btrim(content_reference),'') is not null)
                       and exists (
                         select 1 from intelligence.evidence_attestation t
                           join intelligence.source_authority a
                             on a.id=t.authority_id and a.workspace_id=t.workspace_id
                          where t.evidence_id=intelligence.evidence.id
                            and t.workspace_id=:ws
                            and t.attested_hash=intelligence.evidence.content_hash
                            and t.attested_at<=clock_timestamp()
                            and (t.valid_until is null or t.valid_until>clock_timestamp())
                            and t.revoked_at is null
                            and a.status='ACTIVE' and a.revoked_at is null
                            and a.valid_from<=clock_timestamp()
                            and (a.valid_until is null or a.valid_until>clock_timestamp())
                            and a.subject_type=intelligence.evidence.subject_type
                            and a.subject_id=intelligence.evidence.subject_id
                            and a.evidence_type=intelligence.evidence.evidence_type
                            and a.source_type=intelligence.evidence.source_type
                            and a.source_identity=intelligence.evidence.source_identity
                            and (a.jurisdiction is null
                                 or a.jurisdiction=intelligence.evidence.jurisdiction)
                       )
                    """).param("id", id).param("ws", workspaceId).query(UUID.class).optional()
                    .orElseThrow(() -> new NoSuchElementException("decision evidence unavailable"));
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
