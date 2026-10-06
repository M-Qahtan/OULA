CREATE SCHEMA IF NOT EXISTS ai;
CREATE TABLE ai.execution (
  id UUID PRIMARY KEY, workspace_id UUID, agent_type VARCHAR(80) NOT NULL, use_case VARCHAR(100) NOT NULL,
  model_provider VARCHAR(80), model_id VARCHAR(120), model_version VARCHAR(80), prompt_version VARCHAR(80),
  input_reference_json JSONB, tool_calls_json JSONB, output_hash VARCHAR(128), confidence NUMERIC(5,4),
  started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, policy_result VARCHAR(40), human_review_status VARCHAR(40)
);
