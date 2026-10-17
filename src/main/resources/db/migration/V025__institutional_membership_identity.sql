-- V025: institution-bound actor identity. No workspace-wide actor access by JWT alone.
-- Provisioning of real members is deliberately NOT exposed to self-service APIs.
CREATE TABLE iam.institutional_membership (
  workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
  actor_id UUID NOT NULL,
  issuer VARCHAR(512) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  member_role VARCHAR(32) NOT NULL CHECK (member_role IN ('ADMIN','BROKER','STAFF')),
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
  provisioning_source VARCHAR(32) NOT NULL
    CHECK (provisioning_source IN ('TRUSTED_BOOTSTRAP','DEV_FIXTURE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  PRIMARY KEY (workspace_id, actor_id),
  CHECK (length(trim(issuer)) > 0 AND length(trim(subject)) > 0),
  CHECK ((status='ACTIVE' AND revoked_at IS NULL)
       OR (status='REVOKED' AND revoked_at IS NOT NULL))
);

CREATE INDEX idx_institution_membership_issuer_subject
  ON iam.institutional_membership(issuer,subject,workspace_id)
  WHERE status='ACTIVE';

COMMENT ON TABLE iam.institutional_membership IS
  'Trusted-provisioning directory, not a license, government verification, or owner mandate. JWT claims alone do not create memberships.';
