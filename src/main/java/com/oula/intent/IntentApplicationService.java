package com.oula.intent;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class IntentApplicationService {
    private static final Set<String> MVP_TYPES = Set.of("BUY", "RENT");

    private final IntentRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public IntentApplicationService(
            IntentRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public IntentView createDraft(
            AccessContext access,
            UpsertIntentCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        validate(command);
        UUID id = UuidV7.next();
        repository.insert(id, access.workspaceId(), access.actorId(), command);
        emit(
                access, "INTENT_CREATED", "intent.intent.created.v1",
                id, correlationId, Map.of("intentType", command.intentType())
        );
        return repository.find(access.workspaceId(), id);
    }

    @Transactional
    public IntentView updateDraft(
            AccessContext access,
            UUID intentId,
            long expectedVersion,
            UpsertIntentCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        validate(command);
        repository.updateDraft(
                access.workspaceId(), intentId, expectedVersion, command
        );
        emit(
                access, "INTENT_UPDATED", "intent.intent.updated.v1",
                intentId, correlationId, Map.of("expectedVersion", expectedVersion)
        );
        return repository.find(access.workspaceId(), intentId);
    }

    @Transactional
    public IntentView activate(
            AccessContext access,
            UUID intentId,
            long expectedVersion,
            UUID correlationId
    ) {
        requireAccess(access);
        repository.activate(access.workspaceId(), intentId, expectedVersion);
        emit(
                access, "INTENT_ACTIVATED", "intent.intent.activated.v1",
                intentId, correlationId, Map.of("expectedVersion", expectedVersion)
        );
        return repository.find(access.workspaceId(), intentId);
    }

    @Transactional(readOnly = true)
    public IntentView get(AccessContext access, UUID intentId) {
        requireAccess(access);
        return repository.find(access.workspaceId(), intentId);
    }

    private void validate(UpsertIntentCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.intentType() == null || !MVP_TYPES.contains(command.intentType())) {
            throw new IllegalArgumentException("Riyadh MVP supports BUY or RENT intent");
        }
        if (command.budgetMax() == null || command.budgetMax().signum() <= 0) {
            throw new IllegalArgumentException("budgetMax must be positive");
        }
        if (command.minimumBedrooms() == null || command.minimumBedrooms() < 0) {
            throw new IllegalArgumentException("minimumBedrooms must be non-negative");
        }
        if (command.preferredDistricts() != null
                && command.preferredDistricts().size() > 25) {
            throw new IllegalArgumentException("too many preferred districts");
        }
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            UUID intentId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(), access.actorId(), access.subject(),
                access.purpose().name(), auditAction, "Intent", intentId,
                correlationId, details
        );
        outbox.append(
                eventType, "Intent", intentId, access.workspaceId(),
                correlationId, correlationId, details
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
    }
}
