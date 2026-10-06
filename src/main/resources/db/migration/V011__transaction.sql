CREATE SCHEMA IF NOT EXISTS tx;
CREATE TABLE tx.transaction (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  transaction_type VARCHAR(40) NOT NULL,
  asset_id UUID NOT NULL,
  originating_intent_id UUID,
  listing_id UUID,
  jurisdiction_code VARCHAR(20) NOT NULL,
  stage VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transaction_workspace_status ON tx.transaction (workspace_id, status, updated_at DESC);
