CREATE SCHEMA IF NOT EXISTS people;
CREATE SCHEMA IF NOT EXISTS spatial;
CREATE SCHEMA IF NOT EXISTS market;
CREATE SCHEMA IF NOT EXISTS decision;
CREATE SCHEMA IF NOT EXISTS docs;
CREATE SCHEMA IF NOT EXISTS compliance;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS integration;

CREATE TABLE IF NOT EXISTS people.person (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  linked_subject VARCHAR(255),
  display_name VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS spatial.place (
  id UUID PRIMARY KEY,
  parent_place_id UUID REFERENCES spatial.place(id),
  place_type VARCHAR(40) NOT NULL,
  name_ar VARCHAR(255),
  name_en VARCHAR(255),
  country_code CHAR(2) NOT NULL,
  geometry geometry,
  centroid geography(Point,4326),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS market.listing (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  property_id UUID NOT NULL REFERENCES property.asset(id),
  transaction_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  asking_price NUMERIC(18,2),
  currency CHAR(3) NOT NULL DEFAULT 'SAR',
  published_at TIMESTAMPTZ,
  withdrawn_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS docs.evidence (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  evidence_type VARCHAR(64) NOT NULL,
  source VARCHAR(160) NOT NULL,
  verification_status VARCHAR(32) NOT NULL,
  content_hash VARCHAR(128),
  captured_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS decision.case_record (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  intent_id UUID NOT NULL REFERENCES intent.intent(id),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS compliance.policy_decision (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  actor_id UUID NOT NULL,
  action VARCHAR(120) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id UUID NOT NULL,
  decision VARCHAR(40) NOT NULL,
  reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
  evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS ops.guardian_signal (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  property_id UUID NOT NULL REFERENCES property.asset(id),
  signal_type VARCHAR(120) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS integration.external_reference (
  id UUID PRIMARY KEY,
  provider_code VARCHAR(80) NOT NULL,
  internal_resource_type VARCHAR(80) NOT NULL,
  internal_resource_id UUID NOT NULL,
  external_resource_type VARCHAR(80) NOT NULL,
  external_resource_id VARCHAR(255) NOT NULL,
  last_verified_at TIMESTAMPTZ,
  UNIQUE(provider_code, external_resource_type, external_resource_id)
);

CREATE INDEX IF NOT EXISTS idx_people_person_workspace ON people.person(workspace_id,status);
CREATE INDEX IF NOT EXISTS idx_spatial_place_centroid ON spatial.place USING GIST(centroid);
CREATE INDEX IF NOT EXISTS idx_market_listing_property_status ON market.listing(property_id,status);
CREATE INDEX IF NOT EXISTS idx_guardian_signal_property ON ops.guardian_signal(property_id,status,detected_at DESC);
