package com.oula.documents;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class VerifiedDocumentEvidenceService {
    private final JdbcClient jdbc;

    public VerifiedDocumentEvidenceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void requireVerified(UUID workspaceId, UUID evidenceId, String expectedType) {
        if (workspaceId == null || evidenceId == null || expectedType == null) {
            throw new IllegalArgumentException("evidence context is required");
        }
        String actualType = jdbc.sql("""
                select evidence_type
                  from docs.evidence
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                   and verification_status = 'VERIFIED'
                """)
                .param("evidenceId", evidenceId)
                .param("workspaceId", workspaceId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("verified evidence not found"));
        if (!expectedType.equals(actualType)) {
            throw new IllegalArgumentException("wrong verified evidence type");
        }
    }
}
