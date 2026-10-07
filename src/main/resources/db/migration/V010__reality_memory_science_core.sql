CREATE TABLE property.state_snapshot (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    version INTEGER NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    state_basis VARCHAR(32) NOT NULL,
    state_json JSONB NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_reference VARCHAR(255),
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    supersedes_snapshot_id UUID REFERENCES property.state_snapshot(id),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_state_snapshot_version UNIQUE(property_id, version),
    CONSTRAINT chk_property_state_snapshot_version CHECK (version > 0),
    CONSTRAINT chk_property_state_snapshot_basis CHECK (
        state_basis IN ('CANONICAL_FACTS','OBSERVATIONS','MIXED','UNKNOWN')
    )
);

CREATE INDEX idx_property_state_snapshot_latest
    ON property.state_snapshot(workspace_id, property_id, effective_at DESC, version DESC);

CREATE OR REPLACE FUNCTION property.reject_state_snapshot_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'property.state_snapshot is immutable; record a new version instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_property_state_snapshot_immutable
BEFORE UPDATE OR DELETE ON property.state_snapshot
FOR EACH ROW EXECUTE FUNCTION property.reject_state_snapshot_mutation();

CREATE TABLE intelligence.reality_gap_policy (
    policy_key VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    within_threshold NUMERIC(10,8) NOT NULL,
    minor_threshold NUMERIC(10,8) NOT NULL,
    material_threshold NUMERIC(10,8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    PRIMARY KEY(policy_key, version),
    CONSTRAINT chk_reality_gap_policy_thresholds CHECK (
        within_threshold >= 0
        AND minor_threshold >= within_threshold
        AND material_threshold >= minor_threshold
    ),
    CONSTRAINT chk_reality_gap_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
);

INSERT INTO intelligence.reality_gap_policy (
    policy_key, version, within_threshold, minor_threshold, material_threshold,
    status, effective_from
) VALUES (
    'commuteMinutes', 'v1', 0.05000000, 0.15000000, 0.30000000,
    'ACTIVE', now()
);

ALTER TABLE intelligence.reality_gap
    ADD COLUMN policy_key VARCHAR(120),
    ADD COLUMN policy_version VARCHAR(40),
    ADD COLUMN classification VARCHAR(40);

UPDATE intelligence.reality_gap
SET policy_key = 'commuteMinutes',
    policy_version = 'v1',
    classification = CASE
        WHEN relative_error IS NULL AND absolute_error = 0 THEN 'WITHIN_EXPECTATION'
        WHEN relative_error IS NULL THEN 'UNKNOWN'
        WHEN relative_error <= 0.05000000 THEN 'WITHIN_EXPECTATION'
        WHEN relative_error <= 0.15000000 THEN 'MINOR_VARIANCE'
        WHEN relative_error <= 0.30000000 THEN 'MATERIAL_VARIANCE'
        ELSE 'SEVERE_VARIANCE'
    END;

ALTER TABLE intelligence.reality_gap
    ALTER COLUMN policy_key SET NOT NULL,
    ALTER COLUMN policy_version SET NOT NULL,
    ALTER COLUMN classification SET NOT NULL,
    ADD CONSTRAINT fk_reality_gap_policy
        FOREIGN KEY(policy_key, policy_version)
        REFERENCES intelligence.reality_gap_policy(policy_key, version),
    ADD CONSTRAINT chk_reality_gap_classification CHECK (
        classification IN (
            'WITHIN_EXPECTATION','MINOR_VARIANCE','MATERIAL_VARIANCE',
            'SEVERE_VARIANCE','UNKNOWN'
        )
    );

CREATE TABLE intelligence.error_hypothesis_review (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    reality_gap_id UUID NOT NULL REFERENCES intelligence.reality_gap(id) ON DELETE CASCADE,
    reviewer_actor_id UUID NOT NULL,
    cause_category VARCHAR(64) NOT NULL,
    cause_confidence NUMERIC(5,4),
    rationale TEXT NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    calibration_status VARCHAR(32) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_error_review_confidence CHECK (
        cause_confidence IS NULL OR cause_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_error_review_status CHECK (review_status IN ('REVIEWED','DISMISSED')),
    CONSTRAINT chk_error_review_calibration CHECK (
        calibration_status IN ('CANDIDATE','EXCLUDED')
    )
);

CREATE TABLE intelligence.error_hypothesis_evidence (
    review_id UUID NOT NULL REFERENCES intelligence.error_hypothesis_review(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
    PRIMARY KEY(review_id, evidence_id)
);

CREATE INDEX idx_error_review_gap
    ON intelligence.error_hypothesis_review(reality_gap_id, reviewed_at DESC);

CREATE OR REPLACE VIEW intelligence.v_reality_case AS
SELECT
    o.workspace_id,
    o.id AS outcome_id,
    d.id AS decision_id,
    r.id AS recommendation_id,
    d.selected_property_id AS property_id,
    snapshot.id AS property_snapshot_id,
    r.model_version_id,
    r.generated_at AS recommendation_generated_at,
    d.decided_at,
    o.observed_at,
    COUNT(g.id) AS reality_gap_count,
    COUNT(g.id) FILTER (WHERE g.review_status = 'PENDING_REVIEW') AS pending_review_count,
    MAX(g.absolute_error) AS max_absolute_error,
    MAX(g.relative_error) AS max_relative_error
FROM intelligence.outcome o
JOIN intelligence.decision_record d ON d.id = o.decision_id
JOIN intelligence.recommendation r ON r.id = d.recommendation_id
LEFT JOIN LATERAL (
    SELECT ps.id
      FROM property.state_snapshot ps
     WHERE ps.workspace_id = o.workspace_id
       AND ps.property_id = d.selected_property_id
       AND ps.effective_at <= d.decided_at
       AND ps.recorded_at <= d.decided_at
     ORDER BY ps.effective_at DESC, ps.version DESC
     LIMIT 1
) snapshot ON TRUE
LEFT JOIN intelligence.reality_gap g ON g.outcome_id = o.id
GROUP BY
    o.workspace_id, o.id, d.id, r.id, d.selected_property_id,
    snapshot.id, r.model_version_id, r.generated_at, d.decided_at, o.observed_at;

CREATE OR REPLACE VIEW intelligence.v_model_calibration_signal AS
SELECT
    model_version_id,
    metric_key,
    policy_key,
    policy_version,
    COUNT(*) AS sample_count,
    AVG(absolute_error) AS avg_absolute_error,
    AVG(relative_error) FILTER (WHERE relative_error IS NOT NULL) AS avg_relative_error,
    COUNT(*) FILTER (WHERE classification = 'MATERIAL_VARIANCE') AS material_variance_count,
    COUNT(*) FILTER (WHERE classification = 'SEVERE_VARIANCE') AS severe_variance_count,
    COUNT(*) FILTER (WHERE review_status = 'REVIEWED') AS reviewed_count,
    COUNT(*) FILTER (WHERE calibration_status = 'CANDIDATE') AS calibration_candidate_count,
    COUNT(*) FILTER (WHERE cause_category = 'UNKNOWN_CAUSE') AS unknown_cause_count,
    MAX(detected_at) AS last_detected_at
FROM intelligence.reality_gap
GROUP BY model_version_id, metric_key, policy_key, policy_version;

COMMENT ON VIEW intelligence.v_reality_case IS
    'Composition/read model only. Canonical truth remains in property and intelligence source tables.';
COMMENT ON VIEW intelligence.v_model_calibration_signal IS
    'Observational calibration projection only; it never mutates model versions or weights.';
