package com.oula.intelligence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.NoSuchElementException;

@Repository
class RealityGapPolicyRepository {
    private final JdbcClient jdbc;

    RealityGapPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    RealityGapPolicy activeFor(String metricKey) {
        return jdbc.sql("""
                select policy_key, version, within_threshold, minor_threshold, material_threshold
                  from intelligence.reality_gap_policy
                 where policy_key = :metricKey
                   and status = 'ACTIVE'
                   and effective_from <= now()
                   and (effective_to is null or effective_to > now())
                 order by effective_from desc
                 limit 1
                """)
                .param("metricKey", metricKey)
                .query((rs, rowNum) -> new RealityGapPolicy(
                        rs.getString("policy_key"),
                        rs.getString("version"),
                        rs.getBigDecimal("within_threshold"),
                        rs.getBigDecimal("minor_threshold"),
                        rs.getBigDecimal("material_threshold")
                ))
                .optional()
                .orElseThrow(() -> new NoSuchElementException(
                        "no active Reality Gap policy for metric " + metricKey
                ));
    }
}
