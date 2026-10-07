package com.oula.documents;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class EvidenceApplicationService {
    private final EvidenceRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public EvidenceApplicationService(
            EvidenceRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public EvidenceArtifactView register(
            AccessContext access,
            String evidenceType,
            String source,
            String contentHash,
            Instant capturedAt,
            UUID correlationId
    ) {
        requireAccess(access);
        requireText(evidenceType, "evidenceType");
        requireText(source, "source");
        requireText(contentHash, "contentHash");
        if (contentHash.length() > 128) {
            throw new IllegalArgumentException("contentHash exceeds 128 characters");
        }

        Instant now = clock.instant();
        Instant captured = capturedAt == null ? now : capturedAt;
        if (captured.isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("capturedAt cannot be materially in the future");
        }

        UUID evidenceId = UuidV7.next();
        repository.insert(
                evidenceId,
                access.workspaceId(),
                evidenceType,
                source,
                contentHash,
                OffsetDateTime.ofInstant(captured, ZoneOffset.UTC)
        );
        emit(
                access,
                "DOCUMENT_EVIDENCE_REGISTERED",
                "documents.evidence.registered.v1",
                evidenceId,
                correlationId,
                Map.of(
                        "evidenceType", evidenceType,
                        "source", source,
                        "verificationStatus", "RECEIVED"
                )
        );
        return repository.find(access.workspaceId(), evidenceId);
    }

    @Transactional
    public EvidenceArtifactView verify(
            AccessContext access,
            UUID evidenceId,
            String reason,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(evidenceId, "evidenceId");
        requireText(reason, "reason");
        repository.verify(
                access.workspaceId(),
                evidenceId,
                access.actorId(),
                reason,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        emit(
                access,
                "DOCUMENT_EVIDENCE_VERIFIED",
                "documents.evidence.verified.v1",
                evidenceId,
                correlationId,
                Map.of("verificationStatus", "VERIFIED")
        );
        return repository.find(access.workspaceId(), evidenceId);
    }

    @Transactional(readOnly = true)
    public EvidenceArtifactView get(AccessContext access, UUID evidenceId) {
        requireAccess(access);
        return repository.find(access.workspaceId(), evidenceId);
    }

    @Transactional(readOnly = true)
    public void requireVerified(AccessContext access, UUID evidenceId) {
        requireAccess(access);
        repository.requireVerified(access.workspaceId(), evidenceId);
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            UUID evidenceId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, "Evidence", evidenceId,
                correlationId, details
        );
        outbox.append(
                eventType, "Evidence", evidenceId, access.workspaceId(),
                correlationId, correlationId, details
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
