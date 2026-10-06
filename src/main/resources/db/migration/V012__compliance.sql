CREATE SCHEMA IF NOT EXISTS compliance;
CREATE TABLE compliance.policy_decision (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, actor_id UUID, actor_type VARCHAR(30),
  resource_type VARCHAR(80) NOT NULL, resource_id UUID, action VARCHAR(100) NOT NULL, purpose VARCHAR(100),
  jurisdiction_code VARCHAR(20) NOT NULL, decision VARCHAR(40) NOT NULL, policy_version VARCHAR(50) NOT NULL,
  reason_codes JSONB, evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(), trace_id VARCHAR(100) NOT NULL
);
