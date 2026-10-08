CREATE TABLE integration.partner (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    partner_code VARCHAR(80) NOT NULL,
    partner_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    jurisdiction VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    verification_evidence_id UUID,
    verified_at TIMESTAMPTZ,
    auth_mode VARCHAR(40) NOT NULL,
    credential_reference VARCHAR(255) NOT NULL,
    inbound_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    outbound_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_integration_partner_code UNIQUE(workspace_id, partner_code),
    CONSTRAINT chk_integration_partner_type CHECK (
        partner_type IN (
            'GOVERNMENT','BANK','INSURER','REGISTRY','IDENTITY',
            'SERVICE_PROVIDER','MAPS','DATA_PROVIDER','OTHER'
        )
    ),
    CONSTRAINT chk_integration_partner_status CHECK (
        status IN ('ACTIVE','SUSPENDED')
    ),
    CONSTRAINT chk_integration_partner_verification CHECK (
        verification_status IN ('UNVERIFIED','VERIFIED','EXPIRED')
    ),
    CONSTRAINT chk_integration_partner_auth_mode CHECK (
        auth_mode IN ('MTLS','JWS','OIDC_CLIENT','SIGNED_WEBHOOK','OTHER')
    ),
    CONSTRAINT chk_verified_integration_partner CHECK (
        verification_status <> 'VERIFIED'
        OR (verification_evidence_id IS NOT NULL AND verified_at IS NOT NULL)
    )
);

CREATE TABLE integration.contract (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    partner_id UUID NOT NULL REFERENCES integration.partner(id),
    contract_key VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    allowed_data_classes JSONB NOT NULL,
    status VARCHAR(24) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_integration_contract_version UNIQUE(
        workspace_id, contract_key, version
    ),
    CONSTRAINT chk_integration_contract_direction CHECK (
        direction IN ('INBOUND','OUTBOUND')
    ),
    CONSTRAINT chk_integration_contract_status CHECK (
        status IN ('ACTIVE','INACTIVE')
    )
);

CREATE INDEX idx_integration_contract_match
    ON integration.contract(
        workspace_id, partner_id, direction, purpose,
        operation, resource_type, status, effective_from
    );

CREATE TABLE integration.inbound_receipt (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    partner_id UUID NOT NULL REFERENCES integration.partner(id),
    contract_id UUID NOT NULL REFERENCES integration.contract(id),
    external_event_id VARCHAR(255) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    data_classes JSONB NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    payload_reference TEXT NOT NULL,
    auth_mode VARCHAR(40) NOT NULL,
    credential_reference VARCHAR(255) NOT NULL,
    authenticated_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,

    CONSTRAINT uq_inbound_partner_event UNIQUE(partner_id, external_event_id),
    CONSTRAINT chk_inbound_receipt_status CHECK (
        status IN ('ACCEPTED','REJECTED')
    )
);

CREATE INDEX idx_inbound_receipt_workspace
    ON integration.inbound_receipt(
        workspace_id, partner_id, received_at DESC
    );

CREATE TABLE integration.outbound_request (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    partner_id UUID NOT NULL REFERENCES integration.partner(id),
    contract_id UUID NOT NULL REFERENCES integration.contract(id),
    operation VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    data_classes JSONB NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    payload_reference TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,

    CONSTRAINT chk_outbound_request_status CHECK (
        status IN ('PREPARED','DISPATCHED','ACKNOWLEDGED','FAILED','CANCELLED')
    )
);

CREATE INDEX idx_outbound_request_workspace
    ON integration.outbound_request(
        workspace_id, partner_id, status, created_at DESC
    );

COMMENT ON TABLE integration.partner IS
    'Workspace-scoped external integration identity metadata. credential_reference points to external secret/key management; OULA does not store the secret here.';
COMMENT ON TABLE integration.contract IS
    'Purpose and data-minimization contract for one exact integration operation and direction.';
COMMENT ON TABLE integration.inbound_receipt IS
    'Authenticated metadata receipt with replay protection. Raw external payload is not stored in this table.';
COMMENT ON TABLE integration.outbound_request IS
    'Prepared provider-neutral outbound request metadata. PREPARED does not mean network dispatch occurred.';
