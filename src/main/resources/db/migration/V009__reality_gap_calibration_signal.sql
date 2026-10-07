CREATE TABLE intelligence.reality_gap (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    outcome_id UUID NOT NULL REFERENCES intelligence.outcome(id) ON DELETE CASCADE,
    decision_id UUID NOT NULL REFERENCES intelligence.decision_record(id),
    recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id),
    model_version_id UUID NOT NULL REFERENCES intelligence.model_version(id),
    metric_key VARCHAR(120) NOT NULL,
    unit VARCHAR(40),
    expected_value NUMERIC(18,6) NOT NULL,
    actual_value NUMERIC(18,6) NOT NULL,
    signed_error NUMERIC(18,6) NOT NULL,
    absolute_error NUMERIC(18,6) NOT NULL,
    relative_error NUMERIC(18,8),
    cause_category VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN_CAUSE',
    cause_confidence NUMERIC(5,4),
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    calibration_status VARCHAR(32) NOT NULL DEFAULT 'UNASSESSED',
    detected_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_intelligence_reality_gap_outcome_metric UNIQUE(outcome_id, metric_key),
    CONSTRAINT chk_reality_gap_absolute_error CHECK (absolute_error >= 0),
    CONSTRAINT chk_reality_gap_relative_error CHECK (relative_error IS NULL OR relative_error >= 0),
    CONSTRAINT chk_reality_gap_cause_confidence CHECK (
        cause_confidence IS NULL OR cause_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_reality_gap_cause_category CHECK (
        cause_category IN (
            'DATA_ERROR',
            'MISSING_VARIABLE',
            'MODEL_ERROR',
            'BEHAVIORAL_SHIFT',
            'MARKET_SHOCK',
            'REGULATORY_CHANGE',
            'SPATIAL_CHANGE',
            'EXECUTION_FAILURE',
            'MEASUREMENT_ERROR',
            'UNKNOWN_CAUSE'
        )
    ),
    CONSTRAINT chk_reality_gap_review_status CHECK (
        review_status IN ('PENDING_REVIEW', 'REVIEWED', 'DISMISSED')
    ),
    CONSTRAINT chk_reality_gap_calibration_status CHECK (
        calibration_status IN ('UNASSESSED', 'CANDIDATE', 'EXCLUDED', 'CONSUMED')
    )
);

CREATE INDEX idx_reality_gap_workspace_outcome
    ON intelligence.reality_gap(workspace_id, outcome_id);

CREATE INDEX idx_reality_gap_model_metric
    ON intelligence.reality_gap(model_version_id, metric_key, detected_at DESC);

COMMENT ON COLUMN intelligence.reality_gap.signed_error IS
    'Scientific error convention: expected_value - actual_value.';

COMMENT ON COLUMN intelligence.reality_gap.calibration_status IS
    'No model update is automatic. UNASSESSED gaps require later evidence-backed validation before becoming calibration candidates.';
