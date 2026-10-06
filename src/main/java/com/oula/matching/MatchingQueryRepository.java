package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.intent.IntentStatus;
import com.oula.property.PropertyCandidate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Repository
class MatchingQueryRepository {
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    MatchingQueryRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    Intent loadIntent(UUID intentId, UUID workspaceId) {
        return jdbc.sql("""
                select id, workspace_id, budget_max, minimum_bedrooms,
                       preferred_districts::text, status
                  from intent.intent
                 where id = :intentId
                   and workspace_id = :workspaceId
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new Intent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getBigDecimal("budget_max"),
                        rs.getInt("minimum_bedrooms"),
                        parseDistricts(rs.getString("preferred_districts")),
                        IntentStatus.valueOf(rs.getString("status"))
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("intent not found"));
    }

    List<PropertyCandidate> loadCandidates(UUID intentId, UUID workspaceId) {
        return jdbc.sql("""
                select p.id,
                       p.workspace_id,
                       p.asking_price,
                       p.bedrooms,
                       p.district,
                       signal.commute_minutes,
                       count(f.id) filter (where f.truth_status = 'VERIFIED') as verified_facts,
                       count(f.id) as total_facts
                  from property.asset p
                  join matching.intent_property_signal signal
                    on signal.property_id = p.id
                   and signal.intent_id = :intentId
                  left join property.fact f
                    on f.property_id = p.id
                 where p.workspace_id = :workspaceId
                   and p.asking_price is not null
                   and p.bedrooms is not null
                   and p.district is not null
                 group by p.id, p.workspace_id, p.asking_price, p.bedrooms, p.district, signal.commute_minutes
                """)
                .param("intentId", intentId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new PropertyCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getBigDecimal("asking_price"),
                        rs.getInt("bedrooms"),
                        rs.getString("district"),
                        rs.getInt("commute_minutes"),
                        rs.getInt("verified_facts"),
                        rs.getInt("total_facts")
                ))
                .list();
    }

    private Set<String> parseDistricts(String json) {
        try {
            String[] districts = jsonMapper.readValue(json, String[].class);
            return Set.copyOf(Arrays.asList(districts));
        } catch (Exception ex) {
            throw new IllegalStateException("invalid preferred_districts JSON", ex);
        }
    }
}
