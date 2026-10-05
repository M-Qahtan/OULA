CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS intent;
CREATE SCHEMA IF NOT EXISTS property;
CREATE SCHEMA IF NOT EXISTS matching;
CREATE SCHEMA IF NOT EXISTS tx;

CREATE TABLE IF NOT EXISTS iam.workspace (
  id UUID PRIMARY KEY,
  workspace_type VARCHAR(32) NOT NULL,
  name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS platform.outbox_event (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(200) NOT NULL,
  aggregate_type VARCHAR(120) NOT NULL,
  aggregate_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  correlation_id UUID NOT NULL,
  causation_id UUID,
  occurred_at TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  publish_attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON platform.outbox_event (occurred_at) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS platform.idempotency_record (
  workspace_id UUID NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  response_status INTEGER,
  response_body JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (workspace_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS platform.audit_log (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  actor_id UUID,
  action VARCHAR(200) NOT NULL,
  aggregate_type VARCHAR(120),
  aggregate_id UUID,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  trace_id UUID,
  details JSONB NOT NULL DEFAULT '{}'::jsonb
);
