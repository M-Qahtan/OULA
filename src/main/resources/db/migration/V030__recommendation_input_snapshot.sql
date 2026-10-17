-- V030: byte-preserving decision-input snapshot captured atomically at recommendation generation.
-- Hash is a local integrity checksum, not an external signature or WORM guarantee.
CREATE TABLE intelligence.recommendation_input_snapshot (
 recommendation_id UUID PRIMARY KEY REFERENCES intelligence.recommendation(id),
 workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
 schema_version VARCHAR(80) NOT NULL CHECK(schema_version='OULA_RECOMMENDATION_INPUT_V1'),
 payload_json TEXT NOT NULL,
 payload_hash CHAR(64) NOT NULL,
 captured_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(length(payload_json)>2),
 CHECK(payload_json::jsonb ? 'schema'),
 CHECK(payload_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_recommendation_input_snapshot_workspace
 ON intelligence.recommendation_input_snapshot(workspace_id,captured_at DESC);
COMMENT ON TABLE intelligence.recommendation_input_snapshot IS
 'Historical byte-exact input manifest and local SHA-256 integrity check; no regulated signature, model calibration, or external trust assertion.';
