-- Lightweight hooks for OULA's long-term Intelligence Kernel.
-- These structures record evidence, reproducible simulations and engineering artifacts.
-- They do not add heavy solvers or engineering engines to the Riyadh MVP.

CREATE TABLE intelligence.evidence (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  subject_type VARCHAR(80) NOT NULL,
  subject_id UUID NOT NULL,
  evidence_type VARCHAR(80) NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  source_identity VARCHAR(200),
  captured_at TIMESTAMPTZ,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  content_reference TEXT,
  content_hash VARCHAR(128) NOT NULL,
  verification_status VARCHAR(32) NOT NULL,
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  sensitivity VARCHAR(32) NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_intelligence_evidence_subject
  ON intelligence.evidence(subject_type, subject_id, received_at DESC);

CREATE TABLE intelligence.simulation_run (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  simulation_type VARCHAR(80) NOT NULL,
  model_version_id UUID REFERENCES intelligence.model_version(id),
  scenario_reference VARCHAR(160),
  inputs_snapshot JSONB NOT NULL,
  facts_used JSONB NOT NULL DEFAULT '[]'::jsonb,
  evidence_used JSONB NOT NULL DEFAULT '[]'::jsonb,
  assumptions_used JSONB NOT NULL DEFAULT '[]'::jsonb,
  constraints_used JSONB NOT NULL DEFAULT '[]'::jsonb,
  solver VARCHAR(120),
  solver_version VARCHAR(80),
  configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
  random_seed BIGINT,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL,
  outputs JSONB,
  uncertainty JSONB,
  validation_result JSONB
);

CREATE INDEX idx_intelligence_simulation_workspace
  ON intelligence.simulation_run(workspace_id, started_at DESC);

CREATE TABLE intelligence.engineering_artifact (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  artifact_type VARCHAR(80) NOT NULL,
  discipline VARCHAR(80),
  format VARCHAR(80) NOT NULL,
  artifact_version VARCHAR(80) NOT NULL,
  status VARCHAR(32) NOT NULL,
  generated_by VARCHAR(160),
  approved_by UUID,
  object_storage_reference TEXT NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  source_model_reference VARCHAR(160),
  jurisdiction VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE intelligence.recommendation
  ADD COLUMN authority_requirement VARCHAR(48) NOT NULL DEFAULT 'HUMAN_APPROVAL',
  ADD COLUMN uncertainty JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN supporting_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN valid_until TIMESTAMPTZ;

ALTER TABLE intelligence.decision_record
  ADD COLUMN authority VARCHAR(80),
  ADD COLUMN approval_reference VARCHAR(200);

ALTER TABLE intelligence.outcome
  ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN confidence NUMERIC(5,4),
  ADD CONSTRAINT chk_intelligence_outcome_confidence
    CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1);
