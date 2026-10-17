-- V026: Office roster admission requires admin invitation AND target's authenticated acceptance.
-- A token claiming a workspace never creates a membership.
CREATE TABLE iam.institutional_member_invitation (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  actor_id UUID NOT NULL,
  issuer VARCHAR(512) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  member_role VARCHAR(32) NOT NULL CHECK (member_role IN ('BROKER', 'STAFF')),
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','ACCEPTED')),
  invited_by UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  UNIQUE (workspace_id,actor_id),
  CHECK (length(trim(issuer)) > 0 AND length(trim(subject)) > 0),
  CHECK (expires_at > created_at),
  CHECK ((status='PENDING' AND accepted_at IS NULL)
      OR (status='ACCEPTED' AND accepted_at IS NOT NULL))
);

CREATE INDEX idx_iam_member_invitation_status
  ON iam.institutional_member_invitation(workspace_id,status,expires_at);

-- Real identities admitted by accepted administrator invitations have a distinct provenance.
ALTER TABLE iam.institutional_membership
  DROP CONSTRAINT institutional_membership_provisioning_source_check;
ALTER TABLE iam.institutional_membership
  ADD CONSTRAINT institutional_membership_provisioning_source_check
    CHECK (provisioning_source IN
      ('TRUSTED_BOOTSTRAP','DEV_FIXTURE','ADMIN_INVITATION'));

COMMENT ON TABLE iam.institutional_member_invitation IS
  'Office admin can invite BROKER/STAFF only. Invitee JWT issuer+subject+actor must match before admission; never auto-admin.';
