CREATE SCHEMA IF NOT EXISTS people;
CREATE TABLE people.person (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  linked_user_id UUID,
  display_name VARCHAR(255) NOT NULL,
  preferred_locale VARCHAR(20) NOT NULL DEFAULT 'ar-SA',
  status VARCHAR(30) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE people.household (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  name VARCHAR(255),
  household_type VARCHAR(40),
  status VARCHAR(30) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
