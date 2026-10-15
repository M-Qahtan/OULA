ALTER TABLE intent.intent
  ADD CONSTRAINT uq_intent_id_workspace UNIQUE (id, workspace_id);

ALTER TABLE property.asset
  ADD CONSTRAINT uq_property_id_workspace UNIQUE (id, workspace_id);

ALTER TABLE tx.transaction
  ADD CONSTRAINT fk_tx_intent_workspace
  FOREIGN KEY (intent_id, workspace_id)
  REFERENCES intent.intent(id, workspace_id);

ALTER TABLE tx.transaction
  ADD CONSTRAINT fk_tx_property_workspace
  FOREIGN KEY (property_id, workspace_id)
  REFERENCES property.asset(id, workspace_id);

CREATE UNIQUE INDEX uq_tx_open_intent_property
  ON tx.transaction(workspace_id, intent_id, property_id)
  WHERE status = 'OPEN'
    AND intent_id IS NOT NULL
    AND property_id IS NOT NULL;

CREATE TABLE tx.viewing_request (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES tx.transaction(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  preferred_at TIMESTAMPTZ,
  note VARCHAR(500),
  status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
  requested_by UUID NOT NULL,
  human_confirmed BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status IN ('REQUESTED', 'CANCELLED', 'COMPLETED')),
  CHECK (human_confirmed)
);

CREATE INDEX idx_viewing_request_transaction_created
  ON tx.viewing_request(transaction_id, created_at DESC);

CREATE TABLE tx.transaction_offer (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES tx.transaction(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  terms_summary VARCHAR(1000),
  status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  submitted_by UUID NOT NULL,
  human_confirmed BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (amount > 0),
  CHECK (char_length(currency) = 3),
  CHECK (status IN ('SUBMITTED', 'WITHDRAWN', 'ACCEPTED', 'REJECTED')),
  CHECK (human_confirmed)
);

CREATE INDEX idx_transaction_offer_transaction_created
  ON tx.transaction_offer(transaction_id, created_at DESC);
