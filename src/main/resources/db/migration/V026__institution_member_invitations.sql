-- V026: manager-issued, identity-bound office membership invitations.
-- This table never creates an ADMIN membership; only trusted bootstrap may provision ADMIN.
CREATE TABLE iam.institutional_member_invitation (
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  actor_id UUID NOT NULL,
  issuer VARCHAR(512) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  member_role VARCHAR(32) NOT NULL CHECK (member_role IN ('BROKER','STAFF')),
  status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REVOKED')),
  invited_by UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  PRIMARY KEY (workspace_id, actor_id),
  CHECK (length(trim(issuer)) > 0 AND length(trim(subject)) > 0),
  CHECK (
    (status='PENDING' AND accepted_at IS NULL AND revoked_at IS NULL) OR
    (status='ACCEPTED' AND accepted_at IS NOT NULL AND revoked_at IS NULL) OR
    (status='REVOKED' AND revoked_at IS NOT NULL)
  )
);

CREATE INDEX idx_office_invitation_pending_actor
  ON iam.institutional_member_invitation(issuer,subject,actor_id,workspace_id)
  WHERE status='PENDING';

COMMENT ON TABLE iam.institutional_member_invitation IS
  'An invitation only. No institutional API access until the exact signed JWT recipient accepts and an ACTIVE membership row is created.';

-- Distinguish consented onboarding from externally audited operator bootstrap.
ALTER TABLE iam.institutional_membership
  DROP CONSTRAINT institutional_membership_provisioning_source_check;
ALTER TABLE iam.institutional_membership
  ADD CONSTRAINT ck_institutional_membership_provisioning_source
  CHECK (provisioning_source IN ('TRUSTED_BOOTSTRAP','DEV_FIXTURE','INVITATION_ACCEPTED'));
