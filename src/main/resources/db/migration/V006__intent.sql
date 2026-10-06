CREATE SCHEMA IF NOT EXISTS intent;
CREATE TABLE intent.intent (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  actor_type VARCHAR(30) NOT NULL,
  actor_id UUID NOT NULL,
  intent_type VARCHAR(40) NOT NULL,
  asset_type VARCHAR(50),
  currency CHAR(3) NOT NULL,
  budget_min NUMERIC(19,4),
  budget_max NUMERIC(19,4),
  target_from DATE,
  target_to DATE,
  seriousness_score NUMERIC(5,4),
  financing_readiness NUMERIC(5,4),
  status VARCHAR(30) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_intent_budget CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
);
CREATE INDEX idx_intent_workspace_status ON intent.intent (workspace_id, status, intent_type);
