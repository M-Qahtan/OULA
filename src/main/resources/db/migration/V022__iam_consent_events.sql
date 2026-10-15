CREATE TABLE iam.consent_event (
  id UUID PRIMARY KEY,
  actor_id UUID NOT NULL,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  purpose VARCHAR(80) NOT NULL,
  consent_code VARCHAR(120) NOT NULL,
  decision VARCHAR(16) NOT NULL,
  reason VARCHAR(500),
  source VARCHAR(80) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id UUID NOT NULL,
  CHECK (decision IN ('GRANTED', 'REVOKED'))
);

CREATE INDEX idx_consent_event_current
  ON iam.consent_event(actor_id, workspace_id, purpose, consent_code, occurred_at DESC, id DESC);
