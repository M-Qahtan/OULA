CREATE TABLE IF NOT EXISTS platform.inbox_message (
  consumer_name VARCHAR(120) NOT NULL,
  event_id UUID NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL,
  PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS platform.audit_event (
  id UUID PRIMARY KEY,
  workspace_id UUID,
  actor_type VARCHAR(40) NOT NULL,
  actor_id UUID,
  action VARCHAR(120) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id UUID,
  request_id VARCHAR(120),
  trace_id VARCHAR(120),
  before_hash VARCHAR(128),
  after_hash VARCHAR(128),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON platform.audit_event(resource_type, resource_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS iam.membership (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  user_id UUID NOT NULL,
  membership_role VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_membership_workspace_user ON iam.membership(workspace_id, user_id, status);

CREATE TABLE IF NOT EXISTS iam.consent_grant (
  id UUID PRIMARY KEY,
  subject_id UUID NOT NULL,
  grantee_id UUID,
  purpose_code VARCHAR(120) NOT NULL,
  scope_json JSONB NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  policy_version VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS people.household_member (
  id UUID PRIMARY KEY,
  household_id UUID NOT NULL,
  person_id UUID NOT NULL,
  relationship_type VARCHAR(50),
  decision_role VARCHAR(50),
  valid_from DATE,
  valid_to DATE
);
CREATE INDEX IF NOT EXISTS idx_household_member_active ON people.household_member(household_id, person_id, valid_to);

CREATE TABLE IF NOT EXISTS intent.intent_constraint (
  id UUID PRIMARY KEY,
  intent_id UUID NOT NULL,
  constraint_code VARCHAR(100) NOT NULL,
  operator VARCHAR(30) NOT NULL,
  value_json JSONB NOT NULL,
  constraint_type VARCHAR(30) NOT NULL,
  importance NUMERIC(5,4),
  source VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_intent_constraint ON intent.intent_constraint(intent_id, constraint_type);

CREATE TABLE IF NOT EXISTS matching.score_component (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL,
  component_code VARCHAR(60) NOT NULL,
  raw_score NUMERIC(7,4) NOT NULL,
  weighted_score NUMERIC(7,4) NOT NULL,
  weight NUMERIC(7,6) NOT NULL,
  explanation_code VARCHAR(120),
  evidence_json JSONB
);
CREATE INDEX IF NOT EXISTS idx_score_component_candidate ON matching.score_component(candidate_id);

CREATE TABLE IF NOT EXISTS tx.participant (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL,
  party_type VARCHAR(30) NOT NULL,
  party_id UUID NOT NULL,
  role VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_tx_participant ON tx.participant(transaction_id, role, status);

CREATE TABLE IF NOT EXISTS tx.viewing (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(30) NOT NULL,
  requested_by UUID NOT NULL,
  completed_at TIMESTAMPTZ,
  feedback_json JSONB
);

CREATE TABLE IF NOT EXISTS tx.offer (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL,
  offered_by_party_id UUID NOT NULL,
  amount NUMERIC(19,4) NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(30) NOT NULL,
  expires_at TIMESTAMPTZ,
  supersedes_offer_id UUID,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tx_offer_current ON tx.offer(transaction_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS tx.stage_history (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL,
  from_stage VARCHAR(40),
  to_stage VARCHAR(40) NOT NULL,
  changed_by UUID,
  reason_code VARCHAR(100),
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tx_stage_history ON tx.stage_history(transaction_id, changed_at);
