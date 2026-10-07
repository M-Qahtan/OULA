package com.oula.documents;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class EvidenceRepository {
    private final JdbcClient jdbc;

    EvidenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(
            UUID id,
            UUID workspaceId,
            String evidenceType,
            String source,
            String contentHash,
            OffsetDateTime capturedAt
    ) {
        jdbc.sql("""
                insert into docs.evidence
                    (id, workspace_id, evidence_type, source, verification_status,
                     content_hash, captured_at)
                values
                    (:id, :workspaceId, :evidenceType, :source, 'RECEIVED',
                     :contentHash, :capturedAt)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("evidenceType", evidenceType)
                .param("source", source)
                .param("contentHash", contentHash)
                .param("capturedAt", capturedAt)
                .update();
    }

    EvidenceArtifactView find(UUID workspaceId, UUID evidenceId) {
        return jdbc.sql("""
                select id, workspace_id, evidence_type, source, verification_status,
                       content_hash, captured_at, verified_by, verified_at,
                       verification_reason
                  from docs.evidence
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                """)
                .param("evidenceId", evidenceId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new EvidenceArtifactView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("evidence_type"),
                        rs.getString("source"),
                        rs.getString("verification_status"),
                        rs.getString("content_hash"),
                        rs.getObject("captured_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("verified_by", UUID.class),
                        rs.getObject("verified_at", OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("verified_at", OffsetDateTime.class).toInstant(),
                        rs.getString("verification_reason")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("evidence not found"));
    }

    void verify(
            UUID workspaceId,
            UUID evidenceId,
            UUID actorId,
            String reason,
            OffsetDateTime verifiedAt
    ) {
        int updated = jdbc.sql("""
                update docs.evidence
                   set verification_status = 'VERIFIED',
                       verified_by = :actorId,
                       verified_at = :verifiedAt,
                       verification_reason = :reason
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                   and verification_status = 'RECEIVED'
                """)
                .param("evidenceId", evidenceId)
                .param("workspaceId", workspaceId)
                .param("actorId", actorId)
                .param("verifiedAt", verifiedAt)
                .param("reason", reason)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("evidence cannot be verified from current state");
        }
    }

    void requireVerified(UUID workspaceId, UUID evidenceId) {
        Integer count = jdbc.sql("""
                select count(*)
                  from docs.evidence
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                   and verification_status = 'VERIFIED'
                """)
                .param("evidenceId", evidenceId)
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
        if (count == null || count != 1) {
            throw new IllegalStateException("verified evidence is required");
        }
    }
}
