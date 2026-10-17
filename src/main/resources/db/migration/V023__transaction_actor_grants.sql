-- V023: explicit per-transaction actor visibility, fail closed for unassigned actors.
-- No implicit workspace-wide access to individual deal rooms.
ALTER TABLE tx.transaction
  ADD CONSTRAINT uq_tx_transaction_workspace UNIQUE (id, workspace_id);

CREATE TABLE tx.actor_deal_grant (
  transaction_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  actor_id UUID NOT NULL,
  role VARCHAR(24) NOT NULL CHECK (role IN ('ORIGINATOR', 'VIEWER')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  granted_by UUID NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  PRIMARY KEY (transaction_id, actor_id),
  CONSTRAINT fk_deal_grant_workspace FOREIGN KEY (transaction_id,workspace_id)
    REFERENCES tx.transaction (id,workspace_id) ON DELETE CASCADE,
  CHECK (expires_at IS NULL OR expires_at > granted_at),
  CHECK (active OR role = 'VIEWER'),
  CHECK ((active AND revoked_at IS NULL) OR
         (NOT active AND revoked_at IS NOT NULL))
);

CREATE INDEX idx_deal_grant_actor_visibility
  ON tx.actor_deal_grant(workspace_id,actor_id,transaction_id)
  WHERE active;

-- Only grant legacy originators supported by immutable creation history.
-- Historical records without provenance deliberately receive no implicit access.
INSERT INTO tx.actor_deal_grant
    (transaction_id, workspace_id, actor_id, role, active, granted_by)
SELECT DISTINCT ON (t.id)
       t.id, t.workspace_id, h.actor_id, 'ORIGINATOR', TRUE, h.actor_id
  FROM tx.transaction t
  JOIN tx.stage_history h ON h.transaction_id=t.id
 WHERE h.from_stage IS NULL AND h.actor_id IS NOT NULL
 ORDER BY t.id, h.occurred_at ASC, h.id ASC
ON CONFLICT DO NOTHING;
