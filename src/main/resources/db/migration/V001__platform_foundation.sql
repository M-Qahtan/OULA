CREATE SCHEMA IF NOT EXISTS platform;
CREATE TABLE platform.outbox_event (
  id UUID PRIMARY KEY,
  producer_module VARCHAR(50) NOT NULL,
  event_type VARCHAR(150) NOT NULL,
  schema_version INTEGER NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id UUID NOT NULL,
  workspace_id UUID,
  correlation_id UUID,
  causation_id UUID,
  payload_json JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  publish_attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON platform.outbox_event (occurred_at) WHERE published_at IS NULL;

CREATE TABLE platform.idempotency_record (
  workspace_id UUID NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  operation VARCHAR(120) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  response_status INTEGER,
  response_body_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (workspace_id, operation, idempotency_key)
);
