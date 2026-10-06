CREATE SCHEMA IF NOT EXISTS docs;
CREATE TABLE docs.document (
  id UUID PRIMARY KEY, workspace_id UUID NOT NULL, document_type VARCHAR(60) NOT NULL,
  classification VARCHAR(40) NOT NULL, status VARCHAR(30) NOT NULL, current_version_id UUID,
  created_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE docs.document_version (
  id UUID PRIMARY KEY, document_id UUID NOT NULL, object_key TEXT NOT NULL, sha256 VARCHAR(64) NOT NULL,
  mime_type VARCHAR(120) NOT NULL, size_bytes BIGINT NOT NULL, malware_status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
