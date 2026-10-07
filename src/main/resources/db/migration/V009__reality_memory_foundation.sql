CREATE TABLE property.state_snapshot (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  property_id UUID NOT NULL REFERENCES property.asset(id),
  effective_at TIMESTAMPTZ NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  physical_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  market_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  occupancy_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  operational_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
  truth_status VARCHAR(32) NOT NULL,
  supersedes_snapshot_id UUID REFERENCES property.state_snapshot(id),
  version BIGINT NOT NULL,
  created_by UUID,
  purpose VARCHAR(120) NOT NULL,
  correlation_id UUID NOT NULL,
  UNIQUE(property_id, version),
  UNIQUE(workspace_id, correlation_id),
  CHECK (version > 0)
);

CREATE TABLE property.state_snapshot_evidence (
  snapshot_id UUID NOT NULL REFERENCES property.state_snapshot(id) ON DELETE CASCADE,
  evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
  PRIMARY KEY (snapshot_id, evidence_id)
);

CREATE INDEX idx_property_snapshot_timeline
  ON property.state_snapshot(property_id, effective_at DESC, version DESC);

CREATE TABLE intelligence.reality_gap_policy (
  id UUID PRIMARY KEY,
  policy_key VARCHAR(120) NOT NULL,
  version VARCHAR(40) NOT NULL,
  within_threshold NUMERIC(12,6) NOT NULL,
  minor_threshold NUMERIC(12,6) NOT NULL,
  material_threshold NUMERIC(12,6) NOT NULL,
  status VARCHAR(32) NOT NULL,
  effective_from TIMESTAMPTZ NOT NULL,
  UNIQUE(policy_key, version),
  CHECK (within_threshold >= 0),
  CHECK (minor_threshold >= within_threshold),
  CHECK (material_threshold >= minor_threshold)
);

INSERT INTO intelligence.reality_gap_policy (
  id, policy_key, version, within_threshold, minor_threshold, material_threshold, status, effective_from
) VALUES (
  '00000000-0000-8000-8000-000000000009',
  'default-relative-gap',
  'v1',
  0.050000,
  0.150000,
  0.300000,
  'ACTIVE',
  now()
);

CREATE TABLE intelligence.reality_gap (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  outcome_id UUID NOT NULL REFERENCES intelligence.outcome(id),
  observation_id UUID REFERENCES intelligence.observation(id),
  policy_id UUID NOT NULL REFERENCES intelligence.reality_gap_policy(id),
  metric_key VARCHAR(160) NOT NULL,
  expected_value NUMERIC(24,8),
  actual_value NUMERIC(24,8),
  absolute_gap NUMERIC(24,8),
  relative_gap NUMERIC(24,8),
  classification VARCHAR(32) NOT NULL,
  measured_at TIMESTAMPTZ NOT NULL,
  correlation_id UUID NOT NULL,
  UNIQUE(workspace_id, correlation_id, metric_key)
);

CREATE INDEX idx_reality_gap_outcome ON intelligence.reality_gap(outcome_id);

CREATE TABLE intelligence.error_hypothesis (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  reality_gap_id UUID NOT NULL REFERENCES intelligence.reality_gap(id),
  category VARCHAR(64) NOT NULL,
  hypothesis TEXT NOT NULL,
  review_status VARCHAR(32) NOT NULL,
  reviewed_by UUID,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  purpose VARCHAR(120) NOT NULL,
  correlation_id UUID NOT NULL,
  UNIQUE(workspace_id, correlation_id)
);

CREATE TABLE intelligence.error_hypothesis_evidence (
  error_hypothesis_id UUID NOT NULL REFERENCES intelligence.error_hypothesis(id) ON DELETE CASCADE,
  evidence_id UUID NOT NULL REFERENCES intelligence.evidence(id),
  PRIMARY KEY (error_hypothesis_id, evidence_id)
);

COMMENT ON TABLE property.state_snapshot IS
  'Immutable temporal property-state representation. Canonical property facts remain in property.asset/property.fact.';

COMMENT ON TABLE intelligence.reality_gap IS
  'Deterministic expected-vs-actual measurement. A gap does not establish causality.';

COMMENT ON TABLE intelligence.error_hypothesis IS
  'Reviewed, evidence-backed hypothesis about a measured gap; not verified causal truth.';
