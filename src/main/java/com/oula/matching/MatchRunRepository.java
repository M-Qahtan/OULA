package com.oula.matching;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class MatchRunRepository {
    private final JdbcClient jdbc;

    MatchRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void start(UUID runId, UUID workspaceId, UUID intentId, UUID correlationId, Instant startedAt) {
        jdbc.sql("""
                insert into matching.match_run
                    (id, workspace_id, intent_id, algorithm_version, started_at, status, correlation_id)
                values
                    (:id, :workspaceId, :intentId, :algorithmVersion, :startedAt, 'RUNNING', :correlationId)
                """)
                .param("id", runId)
                .param("workspaceId", workspaceId)
                .param("intentId", intentId)
                .param("algorithmVersion", "lifefit-v1")
                .param("startedAt", startedAt)
                .param("correlationId", correlationId)
                .update();
    }

    void save(UUID runId, RankedMatch match) {
        jdbc.sql("""
                insert into matching.match_result
                    (match_run_id, property_id, rank, lifefit_score, confidence, explanation)
                values
                    (:runId, :propertyId, :rank, :score, :confidence, cast(:explanation as jsonb))
                """)
                .param("runId", runId)
                .param("propertyId", match.propertyId())
                .param("rank", match.rank())
                .param("score", match.score())
                .param("confidence", match.confidence())
                .param("explanation", explanationJson(match))
                .update();
    }

    void complete(UUID runId, Instant completedAt) {
        int updated = jdbc.sql("""
                update matching.match_run
                   set status = 'COMPLETED',
                       completed_at = :completedAt
                 where id = :id
                   and status = 'RUNNING'
                """)
                .param("id", runId)
                .param("completedAt", completedAt)
                .update();

        if (updated != 1) {
            throw new IllegalStateException("match run was not RUNNING: " + runId);
        }
    }

    private String explanationJson(RankedMatch match) {
        var d = match.dimensions();
        return """
                {"financial":%.2f,"location":%.2f,"household":%.2f,"mobility":%.2f,"truth":%.2f}
                """.formatted(
                d.getOrDefault("financial", 0.0),
                d.getOrDefault("location", 0.0),
                d.getOrDefault("household", 0.0),
                d.getOrDefault("mobility", 0.0),
                d.getOrDefault("truth", 0.0)
        ).trim();
    }
}
