package com.oula.documents;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EvidenceRegistry {
    private final JdbcClient jdbc;

    public EvidenceRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public EvidenceRef register(
            UUID evidenceId,
            UUID workspaceId,
            String evidenceType,
            String source,
            String sourceIdentity,
            String contentReference,
            String contentHash,
            String verificationStatus,
            Instant capturedAt,
            Instant validUntil,
            String jurisdiction
    ) {
        jdbc.sql("""
                insert into docs.evidence
                    (id, workspace_id, evidence_type, source, source_identity,
                     content_reference, content_hash, verification_status,
                     captured_at, valid_until, jurisdiction)
                values
                    (:id, :workspaceId, :evidenceType, :source, :sourceIdentity,
                     :contentReference, :contentHash, :verificationStatus,
                     :capturedAt, :validUntil, :jurisdiction)
                """)
                .param("id", evidenceId)
                .param("workspaceId", workspaceId)
                .param("evidenceType", evidenceType)
                .param("source", source)
                .param("sourceIdentity", sourceIdentity)
                .param("contentReference", contentReference)
                .param("contentHash", contentHash)
                .param("verificationStatus", verificationStatus)
                .param("capturedAt", utc(capturedAt))
                .param("validUntil", utc(validUntil))
                .param("jurisdiction", jurisdiction)
                .update();

        return new EvidenceRef(
                evidenceId,
                workspaceId,
                evidenceType,
                source,
                verificationStatus,
                contentHash
        );
    }

    public void requireAvailable(UUID workspaceId, List<UUID> evidenceIds) {
        for (UUID evidenceId : evidenceIds) {
            Integer count = jdbc.sql("""
                    select count(*)
                      from docs.evidence
                     where id = :id
                       and workspace_id = :workspaceId
                       and verification_status <> 'REJECTED'
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

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
