-- V029: decision-grade provenance requires separately provisioned source authority.
-- There is intentionally no public creation API or automatic promotion of VERIFIED.
ALTER TABLE intelligence.evidence ADD CONSTRAINT uq_evidence_workspace UNIQUE(id,workspace_id);
CREATE TABLE intelligence.source_authority (
 id UUID PRIMARY KEY,
 workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
 subject_type VARCHAR(80) NOT NULL, subject_id UUID NOT NULL,
 evidence_type VARCHAR(80) NOT NULL, source_type VARCHAR(80) NOT NULL,
 source_identity VARCHAR(200) NOT NULL, jurisdiction VARCHAR(80),
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','REVOKED')),
 valid_from TIMESTAMPTZ NOT NULL, valid_until TIMESTAMPTZ,
 revoked_at TIMESTAMPTZ, verification_reference VARCHAR(256) NOT NULL,
 UNIQUE(id,workspace_id),
 CHECK(length(btrim(source_type))>0 AND length(btrim(source_identity))>0),
 CHECK(length(btrim(verification_reference))>0),
 CHECK(valid_until IS NULL OR valid_until>valid_from),
 CHECK((status='ACTIVE' AND revoked_at IS NULL) OR (status='REVOKED' AND revoked_at IS NOT NULL))
);
CREATE TABLE intelligence.evidence_attestation (
 evidence_id UUID PRIMARY KEY, workspace_id UUID NOT NULL, authority_id UUID NOT NULL,
 attested_hash VARCHAR(128) NOT NULL, attested_at TIMESTAMPTZ NOT NULL,
 valid_until TIMESTAMPTZ, revoked_at TIMESTAMPTZ,
 verifier_reference VARCHAR(256) NOT NULL,
 FOREIGN KEY(evidence_id,workspace_id) REFERENCES intelligence.evidence(id,workspace_id),
 FOREIGN KEY(authority_id,workspace_id) REFERENCES intelligence.source_authority(id,workspace_id),
 CHECK(length(btrim(attested_hash))>0 AND length(btrim(verifier_reference))>0),
 CHECK(valid_until IS NULL OR valid_until>attested_at)
);
CREATE INDEX idx_source_authority_active ON intelligence.source_authority(workspace_id,subject_type,subject_id) WHERE status='ACTIVE';
CREATE INDEX idx_attestation_authority ON intelligence.evidence_attestation(authority_id);
COMMENT ON TABLE intelligence.source_authority IS 'Database authority reference is not proof of independent government, source or legal verification.';
