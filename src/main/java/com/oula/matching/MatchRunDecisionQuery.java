package com.oula.matching;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class MatchRunDecisionQuery {
    private final JdbcClient jdbc;

    public MatchRunDecisionQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public MatchRunDecisionView loadCompleted(UUID matchRunId, UUID workspaceId) {
        Header header = jdbc.sql("""
                select id, workspace_id, intent_id, algorithm_version
                  from matching.match_run
                 where id = :matchRunId
                   and workspace_id = :workspaceId
                   and status = 'COMPLETED'
                """)
                .param("matchRunId", matchRunId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> new Header(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("intent_id", UUID.class),
                        rs.getString("algorithm_version")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("completed match run not found"));

        List<MatchAlternativeDecisionView> alternatives = jdbc.sql("""
                select result.property_id,
                       result.rank,
                       result.lifefit_score,
                       result.confidence,
                       result.explanation::text,
                       signal.commute_minutes
                  from matching.match_result result
                  left join matching.intent_property_signal signal
                    on signal.intent_id = :intentId
                   and signal.property_id = result.property_id
                 where result.match_run_id = :matchRunId
                 order by result.rank
                """)
                .param("intentId", header.intentId())
                .param("matchRunId", matchRunId)
                .query((rs, rowNum) -> new MatchAlternativeDecisionView(
                        rs.getObject("property_id", UUID.class),
                        rs.getInt("rank"),
                        rs.getDouble("lifefit_score"),
                        rs.getDouble("confidence"),
                        rs.getString("explanation"),
                        (Integer) rs.getObject("commute_minutes")
                ))
                .list();

        if (alternatives.isEmpty()) {
            throw new IllegalStateException("completed match run has no alternatives");
        }

        return new MatchRunDecisionView(
                header.id(),
                header.workspaceId(),
                header.intentId(),
                header.algorithmVersion(),
                alternatives
        );
    }

    private record Header(UUID id, UUID workspaceId, UUID intentId, String algorithmVersion) {
    }
}
