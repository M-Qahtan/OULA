CREATE SCHEMA IF NOT EXISTS decision;
CREATE TABLE decision.decision_case (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, actor_id UUID NOT NULL, intent_id UUID NOT NULL,
  status VARCHAR(30) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE decision.scenario (
  id UUID PRIMARY KEY, case_id UUID NOT NULL, scenario_type VARCHAR(50) NOT NULL, subject_asset_id UUID,
  label VARCHAR(255), confidence NUMERIC(5,4), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE decision.outcome (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, decision_case_id UUID, intent_id UUID NOT NULL, asset_id UUID,
  transaction_id UUID, outcome_type VARCHAR(60) NOT NULL, outcome_status VARCHAR(40) NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL, measurement_json JSONB, user_satisfaction NUMERIC(5,4), system_expected_json JSONB
);
