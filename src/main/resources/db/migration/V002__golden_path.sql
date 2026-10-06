CREATE TABLE IF NOT EXISTS intent.intent (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  intent_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  budget_max NUMERIC(18,2),
  minimum_bedrooms INTEGER,
  preferred_districts JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (budget_max IS NULL OR budget_max > 0),
  CHECK (minimum_bedrooms IS NULL OR minimum_bedrooms >= 0)
);

CREATE TABLE IF NOT EXISTS property.asset (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  asset_type VARCHAR(32) NOT NULL,
  district VARCHAR(160),
  bedrooms INTEGER,
  asking_price NUMERIC(18,2),
  location geography(Point, 4326),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (bedrooms IS NULL OR bedrooms >= 0),
  CHECK (asking_price IS NULL OR asking_price > 0)
);

CREATE TABLE IF NOT EXISTS property.fact (
  id UUID PRIMARY KEY,
  property_id UUID NOT NULL REFERENCES property.asset(id),
  fact_key VARCHAR(160) NOT NULL,
  value_json JSONB NOT NULL,
  truth_status VARCHAR(32) NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  confidence NUMERIC(5,4),
  valid_from TIMESTAMPTZ,
  valid_to TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE TABLE IF NOT EXISTS matching.match_run (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  intent_id UUID NOT NULL REFERENCES intent.intent(id),
  algorithm_version VARCHAR(80) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL,
  correlation_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS matching.match_result (
  match_run_id UUID NOT NULL REFERENCES matching.match_run(id) ON DELETE CASCADE,
  property_id UUID NOT NULL REFERENCES property.asset(id),
  rank INTEGER NOT NULL,
  lifefit_score NUMERIC(6,2) NOT NULL,
  confidence NUMERIC(5,4) NOT NULL,
  explanation JSONB NOT NULL,
  PRIMARY KEY (match_run_id, property_id),
  CHECK (rank > 0),
  CHECK (lifefit_score >= 0 AND lifefit_score <= 100),
  CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE TABLE IF NOT EXISTS tx.transaction (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  intent_id UUID REFERENCES intent.intent(id),
  property_id UUID REFERENCES property.asset(id),
  stage VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tx.stage_history (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES tx.transaction(id) ON DELETE CASCADE,
  from_stage VARCHAR(32),
  to_stage VARCHAR(32) NOT NULL,
  actor_id UUID,
  reason VARCHAR(500),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  evidence_id UUID
);
