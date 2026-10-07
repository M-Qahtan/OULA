package com.oula.property;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyStateSnapshotService {
    private static final Set<String> ALLOWED_BASES =
            Set.of("CANONICAL_FACTS", "OBSERVATIONS", "MIXED", "UNKNOWN");

    private final PropertyStateSnapshotRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public PropertyStateSnapshotService(
            PropertyStateSnapshotRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public PropertyStateSnapshot record(
            AccessContext access,
            UUID propertyId,
            RecordPropertyStateSnapshotCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(correlationId, "correlationId");
        requireText(command.stateBasis(), "stateBasis");
        requireText(command.sourceType(), "sourceType");
        if (!ALLOWED_BASES.contains(command.stateBasis())) {
            throw new IllegalArgumentException("unsupported stateBasis");
        }
        if (command.state() == null || command.state().isEmpty()) {
            throw new IllegalArgumentException("state snapshot cannot be empty");
        }
        List<UUID> evidenceRefs = command.evidenceRefs() == null
                ? List.of()
                : List.copyOf(command.evidenceRefs());
        if (evidenceRefs.size() > 25) {
            throw new IllegalArgumentException("too many evidence references");
        }

        Instant now = clock.instant();
        Instant effectiveAt = command.effectiveAt() == null ? now : command.effectiveAt();
        if (effectiveAt.isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("effectiveAt cannot be materially in the future");
        }

        repository.lockProperty(access.workspaceId(), propertyId);
        PropertyStateSnapshot previous = repository.latestRecorded(
                access.workspaceId(), propertyId
        );
        int version = previous == null ? 1 : previous.version() + 1;

        PropertyStateSnapshot snapshot = new PropertyStateSnapshot(
                UuidV7.next(),
                access.workspaceId(),
                propertyId,
                version,
                effectiveAt,
                now,
                command.stateBasis(),
                Map.copyOf(command.state()),
                command.sourceType(),
                command.sourceReference(),
                previous == null ? null : previous.snapshotId()
        );
        repository.insert(snapshot, evidenceRefs, correlationId);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("propertyId", propertyId);
        details.put("version", version);
        details.put("stateBasis", snapshot.stateBasis());
        details.put("sourceType", snapshot.sourceType());
        details.put("evidenceCount", evidenceRefs.size());

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "PROPERTY_STATE_SNAPSHOT_RECORDED",
                "PropertyStateSnapshot",
                snapshot.snapshotId(),
                correlationId,
                details
        );
        outbox.append(
                "property.state_snapshot.recorded.v1",
                "PropertyStateSnapshot",
                snapshot.snapshotId(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );

        return snapshot;
    }

    @Transactional(readOnly = true)
    public PropertyStateSnapshot latestKnownAt(
            AccessContext access,
            UUID propertyId,
            Instant asOf
    ) {
        requireAccess(access);
        Objects.requireNonNull(propertyId, "propertyId");
        Instant instant = asOf == null ? clock.instant() : asOf;
        return repository.latestKnownAt(
                access.workspaceId(),
                propertyId,
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        Objects.requireNonNull(access.purpose(), "purpose");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
