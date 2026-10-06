CREATE TABLE platform.user_dashboard_projection (
  workspace_id UUID PRIMARY KEY, payload_json JSONB NOT NULL, projection_version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
