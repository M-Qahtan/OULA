package com.oula.people;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class PeopleRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    PeopleRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<PersonView> findBySubject(UUID workspaceId, String subject) {
        return jdbc.sql("""
                select id, workspace_id, linked_subject, display_name, status
                  from people.person
                 where workspace_id = :workspaceId
                   and linked_subject = :subject
                   and status = 'ACTIVE'
                """)
                .param("workspaceId", workspaceId)
                .param("subject", subject)
                .query((rs, rowNum) -> new PersonView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("linked_subject"),
                        rs.getString("display_name"),
                        rs.getString("status")
                ))
                .optional();
    }

    void insertPerson(PersonView person) {
        jdbc.sql("""
                insert into people.person
                    (id, workspace_id, linked_subject, display_name, status)
                values
                    (:id, :workspaceId, :subject, :displayName, :status)
                """)
                .param("id", person.personId())
                .param("workspaceId", person.workspaceId())
                .param("subject", person.linkedSubject())
                .param("displayName", person.displayName())
                .param("status", person.status())
                .update();
    }

    void requirePerson(UUID workspaceId, UUID personId) {
        Integer count = jdbc.sql("""
                select count(*)
                  from people.person
                 where id = :personId
                   and workspace_id = :workspaceId
                   and status = 'ACTIVE'
                """)
                .param("personId", personId)
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
        if (count == null || count != 1) {
            throw new NoSuchElementException("person not found");
        }
    }

    void lockPerson(UUID workspaceId, UUID personId) {
        jdbc.sql("""
                select id
                  from people.person
                 where id = :personId
                   and workspace_id = :workspaceId
                   and status = 'ACTIVE'
                 for update
                """)
                .param("personId", personId)
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("person not found"));
    }

    HouseholdView insertHousehold(
            UUID householdId,
            UUID workspaceId,
            String name,
            UUID createdBy
    ) {
        jdbc.sql("""
                insert into people.household
                    (id, workspace_id, name, status, created_by)
                values
                    (:id, :workspaceId, :name, 'ACTIVE', :createdBy)
                """)
                .param("id", householdId)
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("createdBy", createdBy)
                .update();
        return new HouseholdView(householdId, workspaceId, name, "ACTIVE");
    }

    void requireHousehold(UUID workspaceId, UUID householdId) {
        Integer count = jdbc.sql("""
                select count(*)
                  from people.household
                 where id = :householdId
                   and workspace_id = :workspaceId
                   and status = 'ACTIVE'
                """)
                .param("householdId", householdId)
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
        if (count == null || count != 1) {
            throw new NoSuchElementException("household not found");
        }
    }

    void addHouseholdMember(UUID householdId, UUID personId, String relationship) {
        jdbc.sql("""
                insert into people.household_member
                    (household_id, person_id, relationship)
                values
                    (:householdId, :personId, :relationship)
                on conflict (household_id, person_id)
                do update set relationship = excluded.relationship, left_at = null
                """)
                .param("householdId", householdId)
                .param("personId", personId)
                .param("relationship", relationship)
                .update();
    }

    LifeGraphSnapshot latestLifeProfile(UUID workspaceId, UUID personId) {
        return jdbc.sql("""
                select person_id, household_id, household_size, max_housing_budget,
                       mobility_anchor_ids::text, recorded_at
                  from people.life_profile_snapshot
                 where workspace_id = :workspaceId
                   and person_id = :personId
                 order by version desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("personId", personId)
                .query((rs, rowNum) -> new LifeGraphSnapshot(
                        rs.getObject("person_id", UUID.class),
                        rs.getObject("household_id", UUID.class),
                        rs.getInt("household_size"),
                        rs.getBigDecimal("max_housing_budget"),
                        readSet(rs.getString("mobility_anchor_ids")),
                        rs.getObject("recorded_at", OffsetDateTime.class).toInstant()
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("life profile not found"));
    }

    int nextLifeProfileVersion(UUID personId) {
        Integer version = jdbc.sql("""
                select coalesce(max(version), 0) + 1
                  from people.life_profile_snapshot
                 where person_id = :personId
                """)
                .param("personId", personId)
                .query(Integer.class)
                .single();
        return version == null ? 1 : version;
    }

    UUID latestLifeProfileId(UUID personId) {
        return jdbc.sql("""
                select id
                  from people.life_profile_snapshot
                 where person_id = :personId
                 order by version desc
                 limit 1
                """)
                .param("personId", personId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    void insertLifeProfile(
            UUID id,
            UUID workspaceId,
            UUID personId,
            int version,
            RecordLifeProfileCommand command,
            UUID supersedes,
            UUID correlationId,
            OffsetDateTime effectiveAt,
            OffsetDateTime recordedAt
    ) {
        jdbc.sql("""
                insert into people.life_profile_snapshot
                    (id, workspace_id, person_id, household_id, version, household_size,
                     max_housing_budget, mobility_anchor_ids, preferences, constraints,
                     effective_at, recorded_at, supersedes_snapshot_id, correlation_id)
                values
                    (:id, :workspaceId, :personId, :householdId, :version, :householdSize,
                     :budget, cast(:anchors as jsonb), cast(:preferences as jsonb),
                     cast(:constraints as jsonb), :effectiveAt, :recordedAt,
                     :supersedes, :correlationId)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("personId", personId)
                .param("householdId", command.householdId())
                .param("version", version)
                .param("householdSize", command.householdSize())
                .param("budget", command.maxHousingBudget())
                .param("anchors", write(command.mobilityAnchors()))
                .param("preferences", write(command.preferences()))
                .param("constraints", write(command.constraints()))
                .param("effectiveAt", effectiveAt)
                .param("recordedAt", recordedAt)
                .param("supersedes", supersedes)
                .param("correlationId", correlationId)
                .update();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize LifeGraph data", ex);
        }
    }

    private Set<UUID> readSet(String value) {
        try {
            return Set.copyOf(json.readValue(value, new TypeReference<java.util.List<UUID>>() {}));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize mobility anchors", ex);
        }
    }
}
