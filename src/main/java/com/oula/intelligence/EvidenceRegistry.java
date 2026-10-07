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

    void verify(
            UUID workspaceId,
            UUID evidenceId,
            UUID actorId,
            String reason,
            Instant verifiedAt
    ) {
        int updated = jdbc.sql("""
                update intelligence.evidence
                   set verification_status = 'VERIFIED',
                       verified_by = :actorId,
                       verified_at = :verifiedAt,
                       verification_reason = :reason
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                   and verification_status = 'UNVERIFIED'
                   and (valid_until is null or valid_until > now())
                """)
                .param("workspaceId", workspaceId)
                .param("evidenceId", evidenceId)
                .param("actorId", actorId)
                .param("verifiedAt", utc(verifiedAt))
                .param("reason", reason)
                .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "intelligence evidence cannot be verified from current state"
            );
        }
    }

    void requireAvailable(UUID workspaceId, List<UUID> evidenceIds) {
        requireStatus(workspaceId, evidenceIds, false);
    }

    void requireVerified(UUID workspaceId, List<UUID> evidenceIds) {
        requireStatus(workspaceId, evidenceIds, true);
    }

    private void requireStatus(
            UUID workspaceId,
            List<UUID> evidenceIds,
            boolean verifiedOnly
    ) {
        for (UUID evidenceId : evidenceIds) {
            Integer count = jdbc.sql("""
                    select count(*)
                      from intelligence.evidence
                     where id = :id
                       and workspace_id = :workspaceId
                       and (
                            (:verifiedOnly = true and verification_status = 'VERIFIED')
                            or
                            (:verifiedOnly = false and verification_status not in ('REJECTED', 'EXPIRED'))
                       )
                       and (valid_until is null or valid_until > now())
                    """)
                    .param("id", evidenceId)
                    .param("workspaceId", workspaceId)
                    .param("verifiedOnly", verifiedOnly)
                    .query(Integer.class)
                    .single();

            if (count == null || count != 1) {
                throw new NoSuchElementException(
                        verifiedOnly
                                ? "verified evidence not available in workspace: " + evidenceId
                                : "evidence not available in workspace: " + evidenceId
                );
            }
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
