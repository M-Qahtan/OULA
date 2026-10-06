CREATE SCHEMA IF NOT EXISTS property;
CREATE TABLE property.asset (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  asset_type VARCHAR(50) NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL,
  primary_place_id UUID,
  canonical_name VARCHAR(255),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_asset_type_not_empty CHECK (length(trim(asset_type)) > 0)
);
CREATE INDEX idx_asset_workspace_status ON property.asset (workspace_id, lifecycle_status);

CREATE TABLE property.fact (
  id UUID PRIMARY KEY,
  asset_id UUID NOT NULL,
  fact_type VARCHAR(100) NOT NULL,
  value_type VARCHAR(30) NOT NULL,
  value_text TEXT,
  value_numeric NUMERIC(19,4),
  value_boolean BOOLEAN,
  value_date DATE,
  value_json JSONB,
  unit_code VARCHAR(30),
  truth_status VARCHAR(30) NOT NULL,
  confidence NUMERIC(5,4),
  source_type VARCHAR(50) NOT NULL,
  source_reference TEXT,
  valid_from TIMESTAMPTZ,
  valid_to TIMESTAMPTZ,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  superseded_at TIMESTAMPTZ,
  CONSTRAINT chk_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);
CREATE INDEX idx_property_fact_current ON property.fact (asset_id, fact_type) WHERE superseded_at IS NULL;
