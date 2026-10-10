package com.oula.intent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Minimal explicit-intent lifecycle, persisting to the existing intent.intent table. */
@Service
public class IntentApplicationService {
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public IntentApplicationService(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public IntentDetails create(
            UUID id,
            UUID workspaceId,
            IntentType intentType,
            BigDecimal budgetMax,
            Integer minimumBedrooms,
            Set<String> preferredDistricts
    ) {
        Objects.requireNonNull(id, "intent id");
        Objects.requireNonNull(workspaceId, "workspace");
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(budgetMax, "budgetMax");
        Objects.requireNonNull(minimumBedrooms, "minimumBedrooms");
        Objects.requireNonNull(preferredDistricts, "preferredDistricts");
        if (budgetMax.signum() <= 0 || minimumBedrooms < 0) {
            throw new IllegalArgumentException("intent constraints are invalid");
        }
        String districts;
        try {
            districts = mapper.writeValueAsString(preferredDistricts.stream().sorted().toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("preferredDistricts cannot be serialized", e);
        }
        jdbc.sql("""
                insert into intent.intent
                  (id, workspace_id, intent_type, status, budget_max, minimum_bedrooms, preferred_districts)
                values (:id, :workspaceId, :intentType, 'ACTIVE', :budgetMax, :minimumBedrooms, cast(:districts as jsonb))
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("intentType", intentType.name())
                .param("budgetMax", budgetMax)
                .param("minimumBedrooms", minimumBedrooms)
                .param("districts", districts)
                .update();
        return get(id, workspaceId);
    }

    @Transactional(readOnly = true)
    public IntentDetails get(UUID id, UUID workspaceId) {
        Objects.requireNonNull(id, "intent id");
        Objects.requireNonNull(workspaceId, "workspace");
        return jdbc.sql("""
                select id, workspace_id, intent_type, status, budget_max, minimum_bedrooms,
                       preferred_districts::text as preferred_districts
                  from intent.intent
                 where id = :id and workspace_id = :workspaceId
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new IntentDetails(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        IntentType.valueOf(rs.getString("intent_type")),
                        IntentStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("budget_max"),
                        (Integer) rs.getObject("minimum_bedrooms"),
                        parseDistricts(rs.getString("preferred_districts"))
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("intent not found"));
    }

    private Set<String> parseDistricts(String json) {
        try {
            return Set.copyOf(Arrays.asList(mapper.readValue(json, String[].class)));
        } catch (Exception e) {
            throw new IllegalStateException("invalid persisted preferredDistricts JSON", e);
        }
    }
}
