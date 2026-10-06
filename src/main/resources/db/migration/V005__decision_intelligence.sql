CREATE SCHEMA IF NOT EXISTS intelligence;

CREATE TABLE intelligence.model_version (
    id UUID PRIMARY KEY,
    model_key VARCHAR(120) NOT NULL,
    version VARCHAR(80) NOT NULL,
    model_type VARCHAR(40) NOT NULL,
    provider VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    risk_class VARCHAR(8) NOT NULL,
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    limitations JSONB NOT NULL DEFAULT '[]'::jsonb,
    released_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (model_key, version)
);

INSERT INTO intelligence.model_version (
    id, model_key, version, model_type, provider, status, risk_class,
    capabilities, limitations, released_at
) VALUES (
    '00000000-0000-8000-8000-000000000001',
    'lifefit-v1',
    '1.0.0',
    'RULE_ENGINE',
    'OULA',
    'ACTIVE',
    'R2',
    '["hard_constraint_filtering","weighted_lifefit_scoring","dimension_explanation"]'::jsonb,
    '["not_a_market_valuation","not_a_legal_determination","confidence_depends_on_available_property_truth"]'::jsonb,
    now()
) ON CONFLICT (model_key, version) DO NOTHING;

CREATE TABLE intelligence.evidence (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    evidence_type VARCHAR(40) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_identity VARCHAR(255),
    content_reference TEXT,
    content_hash VARCHAR(128),
    verification_status VARCHAR(32) NOT NULL,
    captured_at TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    jurisdiction VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_intelligence_evidence_workspace
    ON intelligence.evidence (workspace_id, created_at DESC);

CREATE TABLE intelligence.assumption (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    statement TEXT NOT NULL,
    value_json JSONB,
    source VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    sensitivity VARCHAR(16) NOT NULL,
    valid_until TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE TABLE intelligence.observation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    subject_type VARCHAR(80) NOT NULL,
    subject_id UUID NOT NULL,
    phenomenon VARCHAR(160) NOT NULL,
    value_json JSONB NOT NULL,
    unit VARCHAR(40),
    observed_at TIMESTAMPTZ NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id VARCHAR(255),
    method VARCHAR(160),
    measurement_uncertainty NUMERIC(10,4),
    evidence_id UUID REFERENCES intelligence.evidence(id),
    quality_score NUMERIC(5,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 1))
);

CREATE TABLE intelligence.recommendation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    intent_id UUID NOT NULL REFERENCES intent.intent(id),
    match_run_id UUID NOT NULL REFERENCES matching.match_run(id),
    model_version_id UUID NOT NULL REFERENCES intelligence.model_version(id),
    recommended_property_id UUID NOT NULL REFERENCES property.asset(id),
    reasoning_summary TEXT NOT NULL,
    life_fit_score NUMERIC(6,2) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    confidence_breakdown JSONB NOT NULL,
    uncertainty JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    correlation_id UUID NOT NULL,
    CHECK (life_fit_score >= 0 AND life_fit_score <= 100),
    CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE UNIQUE INDEX uq_recommendation_workspace_correlation
    ON intelligence.recommendation (workspace_id, correlation_id);

CREATE TABLE intelligence.recommendation_alternative (
    recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES property.asset(id),
    rank INTEGER NOT NULL,
    life_fit_score NUMERIC(6,2) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    explanation JSONB NOT NULL,
    expected_commute_minutes INTEGER,
    PRIMARY KEY (recommendation_id, property_id),
    CHECK (rank > 0),
    CHECK (life_fit_score >= 0 AND life_fit_score <= 100),
    CHECK (confidence >= 0 AND confidence <= 1),
    CHECK (expected_commute_minutes IS NULL OR expected_commute_minutes >= 0)
);

CREATE TABLE intelligence.recommendation_evidence (
    recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
    PRIMARY KEY (recommendation_id, evidence_id)
);

CREATE TABLE intelligence.recommendation_assumption (
    recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id) ON DELETE CASCADE,
    assumption_id UUID NOT NULL REFERENCES intelligence.assumption(id),
    PRIMARY KEY (recommendation_id, assumption_id)
);

CREATE TABLE intelligence.decision_record (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id),
    selected_property_id UUID NOT NULL REFERENCES property.asset(id),
    decision_maker UUID NOT NULL,
    accepted_recommendation BOOLEAN NOT NULL,
    override_reason TEXT,
    decided_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL
);

CREATE UNIQUE INDEX uq_decision_workspace_correlation
    ON intelligence.decision_record (workspace_id, correlation_id);

CREATE TABLE intelligence.outcome (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    decision_id UUID NOT NULL REFERENCES intelligence.decision_record(id),
    observation_id UUID REFERENCES intelligence.observation(id),
    expected_metrics JSONB NOT NULL,
    actual_metrics JSONB NOT NULL,
    variance_metrics JSONB NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE UNIQUE INDEX uq_outcome_workspace_correlation
    ON intelligence.outcome (workspace_id, correlation_id);

CREATE TABLE intelligence.outcome_evidence (
    outcome_id UUID NOT NULL REFERENCES intelligence.outcome(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
    PRIMARY KEY (outcome_id, evidence_id)
);
