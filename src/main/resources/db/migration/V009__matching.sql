CREATE SCHEMA IF NOT EXISTS matching;
CREATE TABLE matching.match_run (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, intent_id UUID NOT NULL, algorithm_version VARCHAR(50) NOT NULL,
  candidate_count INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE matching.match_candidate (
  id UUID PRIMARY KEY, match_run_id UUID NOT NULL, asset_id UUID NOT NULL, listing_id UUID,
  overall_score NUMERIC(6,5) NOT NULL, eligible BOOLEAN NOT NULL, rank INTEGER, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_match_candidate_rank ON matching.match_candidate (match_run_id, rank);
