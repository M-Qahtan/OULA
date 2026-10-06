CREATE SCHEMA IF NOT EXISTS market;
CREATE TABLE market.listing (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, asset_id UUID NOT NULL, source_id UUID,
  external_listing_id VARCHAR(200), transaction_type VARCHAR(40) NOT NULL, status VARCHAR(30) NOT NULL,
  published_at TIMESTAMPTZ, withdrawn_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listing_active ON market.listing (status, transaction_type, asset_id);
CREATE TABLE market.listing_version (
  id UUID PRIMARY KEY, listing_id UUID NOT NULL, asking_price NUMERIC(19,4), currency CHAR(3),
  title TEXT, description TEXT, bedrooms INTEGER, bathrooms INTEGER, area_sqm NUMERIC(14,4),
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now(), source_payload_hash VARCHAR(128)
);
CREATE INDEX idx_listing_version_latest ON market.listing_version (listing_id, captured_at DESC);
