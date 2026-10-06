CREATE SCHEMA IF NOT EXISTS intelligence;

CREATE TABLE intelligence.observation (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  subject_type VARCHAR(80) NOT NULL,
  subject_id UUID NOT NULL,
  phenomenon VARCHAR(160) NOT NULL,
  value_json JSONB NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  quality_score NUMERIC(5,4) NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  CHECK (quality_score BETWEEN 0 AND 1)
);

CREATE TABLE intelligence.assumption (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  statement TEXT NOT NULL,
  value_json JSONB NOT NULL,
  source VARCHAR(160) NOT NULL,
  confidence NUMERIC(5,4) NOT NULL,
  sensitivity VARCHAR(32) NOT NULL,
  CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE intelligence.model_version (
  id UUID PRIMARY KEY,
  model_id VARCHAR(120) NOT NULL,
  version VARCHAR(80) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  risk_class VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  released_at TIMESTAMPTZ NOT NULL,
  UNIQUE(model_id,version)
);

CREATE TABLE intelligence.recommendation (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  intent_id UUID NOT NULL REFERENCES intent.intent(id),
  recommended_property_id UUID NOT NULL REFERENCES property.asset(id),
  alternatives JSONB NOT NULL DEFAULT '[]'::jsonb,
  model_id VARCHAR(120) NOT NULL,
  model_version VARCHAR(80) NOT NULL,
  confidence NUMERIC(5,4) NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL,
  correlation_id UUID NOT NULL,
  CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE intelligence.decision_record (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  recommendation_id UUID NOT NULL REFERENCES intelligence.recommendation(id),
  selected_property_id UUID NOT NULL REFERENCES property.asset(id),
  decision_maker UUID NOT NULL,
  accepted_recommendation BOOLEAN NOT NULL,
  override_reason TEXT,
  decided_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE intelligence.outcome (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  decision_id UUID NOT NULL REFERENCES intelligence.decision_record(id),
  outcome_type VARCHAR(120) NOT NULL,
  expected_json JSONB NOT NULL,
  actual_json JSONB NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_intelligence_observation_subject ON intelligence.observation(subject_type,subject_id,observed_at DESC);
CREATE INDEX idx_intelligence_recommendation_intent ON intelligence.recommendation(intent_id,generated_at DESC);
CREATE INDEX idx_intelligence_outcome_decision ON intelligence.outcome(decision_id,observed_at DESC);
