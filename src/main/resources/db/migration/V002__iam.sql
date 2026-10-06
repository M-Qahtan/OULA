CREATE SCHEMA IF NOT EXISTS iam;
CREATE TABLE iam.workspace (
  id UUID PRIMARY KEY,
  workspace_type VARCHAR(30) NOT NULL,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(30) NOT NULL,
  default_locale VARCHAR(20) NOT NULL DEFAULT 'ar-SA',
  default_currency CHAR(3) NOT NULL DEFAULT 'SAR',
  country_code CHAR(2) NOT NULL DEFAULT 'SA',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
