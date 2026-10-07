ALTER TABLE intelligence.evidence
    ADD COLUMN jurisdiction VARCHAR(80);

ALTER TABLE intelligence.assumption
    ADD COLUMN reason TEXT,
    ADD COLUMN valid_until TIMESTAMPTZ,
    ADD COLUMN created_by UUID;

ALTER TABLE intelligence.model_version
    ADD COLUMN runtime_key VARCHAR(120),
    ADD COLUMN provider VARCHAR(160),
    ADD COLUMN capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN limitations JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE UNIQUE INDEX uq_intelligence_model_runtime_key
    ON intelligence.model_version(runtime_key)
    WHERE runtime_key IS NOT NULL;

INSERT INTO intelligence.model_version (
    id, model_id, version, model_type, risk_class, status, released_at,
    runtime_key, provider, capabilities, limitations
) VALUES (
    '00000000-0000-8000-8000-000000000001',
    'LifeFit',
    'v1',
    'RULE_ENGINE',
    'R2',
    'ACTIVE',
    now(),
    'lifefit-v1',
    'OULA',
    '["hard_constraint_filtering","weighted_lifefit_scoring","dimension_explanation"]'::jsonb,
    '["not_a_market_valuation","not_a_legal_determination","confidence_depends_on_available_property_truth"]'::jsonb
) ON CONFLICT (model_id, version) DO UPDATE SET
    runtime_key = EXCLUDED.runtime_key,
    provider = EXCLUDED.provider,
    capabilities = EXCLUDED.capabilities,
    limitations = EXCLUDED.limitations;

ALTER TABLE intelligence.observation
    ADD COLUMN unit VARCHAR(40),
    ADD COLUMN source_id VARCHAR(255),
    ADD COLUMN method VARCHAR(160),
    ADD COLUMN measurement_uncertainty NUMERIC(10,4),
    ADD COLUMN evidence_id UUID REFERENCES intelligence.evidence(id),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE intelligence.recommendation
    ADD COLUMN match_run_id UUID REFERENCES matching.match_run(id),
    ADD COLUMN model_version_id UUID REFERENCES intelligence.model_version(id),
    ADD COLUMN reasoning_summary TEXT,
    ADD COLUMN life_fit_score NUMERIC(6,2),
    ADD COLUMN confidence_breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PROVISIONAL';

CREATE UNIQUE INDEX uq_intelligence_recommendation_correlation
    ON intelligence.recommendation(workspace_id, correlation_id);

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

ALTER TABLE intelligence.decision_record
    ADD COLUMN correlation_id UUID;

CREATE UNIQUE INDEX uq_intelligence_decision_correlation
    ON intelligence.decision_record(workspace_id, correlation_id)
    WHERE correlation_id IS NOT NULL;

ALTER TABLE intelligence.outcome
    ADD COLUMN observation_id UUID REFERENCES intelligence.observation(id),
    ADD COLUMN variance_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN correlation_id UUID;

CREATE UNIQUE INDEX uq_intelligence_outcome_correlation
    ON intelligence.outcome(workspace_id, correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE intelligence.outcome_evidence (
    outcome_id UUID NOT NULL REFERENCES intelligence.outcome(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
    PRIMARY KEY (outcome_id, evidence_id)
);

COMMENT ON COLUMN intelligence.recommendation.supporting_evidence IS
    'Non-authoritative compatibility snapshot. Canonical lineage is intelligence.recommendation_evidence.';

COMMENT ON COLUMN intelligence.outcome.evidence IS
    'Non-authoritative compatibility snapshot. Canonical lineage is intelligence.outcome_evidence.';
