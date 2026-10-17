-- V024: recipient consent gate for transaction-specific read invitations.
-- Never activate a new or historical viewer invitation without their own acceptance.
ALTER TABLE tx.actor_deal_grant
  ADD COLUMN accepted_at TIMESTAMPTZ,
  ADD COLUMN invitation_version BIGINT NOT NULL DEFAULT 1
  CHECK (invitation_version >= 1);

-- Originators remain authorized; pre-existing viewer grants must opt in explicitly.
UPDATE tx.actor_deal_grant
   SET accepted_at=granted_at
 WHERE role='ORIGINATOR' AND active=true;

ALTER TABLE tx.actor_deal_grant
  ADD CONSTRAINT ck_actor_deal_originator_accepted
  CHECK (role='VIEWER' OR accepted_at IS NOT NULL);

COMMENT ON COLUMN tx.actor_deal_grant.accepted_at IS
  'NULL means no recipient acceptance; active VIEWER invitations remain unreadable until explicitly accepted.';
