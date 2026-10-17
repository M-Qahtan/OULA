-- V027: cancellation of pending office onboarding invitations.
-- Do not mutate or erase the original signed-subject invitation record:
-- a separate auditable cancellation row is a permanent acceptance veto.
CREATE TABLE iam.institutional_invitation_cancellation (
  invitation_id UUID PRIMARY KEY
    REFERENCES iam.institutional_member_invitation(id) ON DELETE RESTRICT,
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  cancelled_by UUID NOT NULL,
  cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_iam_invitation_cancellation_workspace
  ON iam.institutional_invitation_cancellation(workspace_id,invitation_id);

COMMENT ON TABLE iam.institutional_invitation_cancellation IS
  'Permanent, admin-audited veto of pending office membership invitation acceptance; invitation provenance is preserved.';
