package com.oula.people;

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
public class PeopleApplicationService {
    private final PeopleRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public PeopleApplicationService(
            PeopleRepository repository,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public PersonView provisionSelf(
            AccessContext access,
            String displayName,
            UUID correlationId
    ) {
        requireAccess(access);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }

        var existing = repository.findBySubject(access.workspaceId(), access.subject());
        if (existing.isPresent()) {
            return existing.get();
        }

        PersonView person = new PersonView(
                UuidV7.next(),
                access.workspaceId(),
                access.subject(),
                displayName.trim(),
                "ACTIVE"
        );
        repository.insertPerson(person);

        emit(
                access,
                "PEOPLE_PERSON_PROVISIONED",
                "people.person.provisioned.v1",
                "Person",
                person.personId(),
                correlationId,
                Map.of("personId", person.personId())
        );
        return person;
    }

    @Transactional
    public HouseholdView createHousehold(
            AccessContext access,
            UUID personId,
            String name,
            UUID correlationId
    ) {
        requireAccess(access);
        repository.requirePerson(access.workspaceId(), personId);
        HouseholdView household = repository.insertHousehold(
                UuidV7.next(),
                access.workspaceId(),
                name == null || name.isBlank() ? null : name.trim(),
                access.actorId()
        );
        repository.addHouseholdMember(household.householdId(), personId, "PRIMARY");

        emit(
                access,
                "PEOPLE_HOUSEHOLD_CREATED",
                "people.household.created.v1",
                "Household",
                household.householdId(),
                correlationId,
                Map.of("householdId", household.householdId(), "primaryPersonId", personId)
        );
        return household;
    }

    @Transactional
    public LifeGraphSnapshot recordLifeProfile(
            AccessContext access,
            UUID personId,
            RecordLifeProfileCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(command, "command");
        if (command.householdSize() < 1) {
            throw new IllegalArgumentException("householdSize must be positive");
        }
        if (command.maxHousingBudget() == null
                || command.maxHousingBudget().signum() <= 0) {
            throw new IllegalArgumentException("maxHousingBudget must be positive");
        }

        Set<UUID> anchors = command.mobilityAnchors() == null
                ? Set.of()
                : Set.copyOf(command.mobilityAnchors());
        if (anchors.size() > 25) {
            throw new IllegalArgumentException("too many mobility anchors");
        }
        Map<String, Object> preferences = command.preferences() == null
                ? Map.of()
                : Map.copyOf(command.preferences());
        Map<String, Object> constraints = command.constraints() == null
                ? Map.of()
                : Map.copyOf(command.constraints());

        RecordLifeProfileCommand normalized = new RecordLifeProfileCommand(
                command.householdId(),
                command.householdSize(),
                command.maxHousingBudget(),
                anchors,
                preferences,
                constraints,
                command.effectiveAt()
        );

        repository.lockPerson(access.workspaceId(), personId);
        if (normalized.householdId() != null) {
            repository.requireHousehold(access.workspaceId(), normalized.householdId());
        }

        Instant now = clock.instant();
        Instant effectiveAt = normalized.effectiveAt() == null ? now : normalized.effectiveAt();
        if (effectiveAt.isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("effectiveAt cannot be materially in the future");
        }

        int version = repository.nextLifeProfileVersion(personId);
        UUID supersedes = repository.latestLifeProfileId(personId);
        UUID snapshotId = UuidV7.next();
        repository.insertLifeProfile(
                snapshotId,
                access.workspaceId(),
                personId,
                version,
                normalized,
                supersedes,
                correlationId,
                OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        );

        emit(
                access,
                "PEOPLE_LIFE_PROFILE_RECORDED",
                "people.life_profile.recorded.v1",
                "LifeProfileSnapshot",
                snapshotId,
                correlationId,
                Map.of(
                        "personId", personId,
                        "version", version,
                        "householdSize", normalized.householdSize()
                )
        );

        return new LifeGraphSnapshot(
                personId,
                normalized.householdId(),
                normalized.householdSize(),
                normalized.maxHousingBudget(),
                anchors,
                now
        );
    }

    @Transactional(readOnly = true)
    public LifeGraphSnapshot latestLifeProfile(AccessContext access, UUID personId) {
        requireAccess(access);
        repository.requirePerson(access.workspaceId(), personId);
        return repository.latestLifeProfile(access.workspaceId(), personId);
    }

    private void emit(
            AccessContext access,
            String auditAction,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            UUID correlationId,
            Map<String, ?> details
    ) {
        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                auditAction,
                aggregateType,
                aggregateId,
                correlationId,
                details
        );
        outbox.append(
                eventType,
                aggregateType,
                aggregateId,
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        if (access.subject() == null || access.subject().isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
    }
}
