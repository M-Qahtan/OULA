CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS leasing;

ALTER TABLE property.asset
    ADD CONSTRAINT uq_property_asset_id_workspace UNIQUE (id, workspace_id);

ALTER TABLE docs.evidence
    ADD CONSTRAINT uq_docs_evidence_id_workspace UNIQUE (id, workspace_id);

CREATE TABLE leasing.lease (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL,
    landlord_party_id UUID NOT NULL,
    tenant_party_id UUID NOT NULL,
    lease_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    rent_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_frequency VARCHAR(24) NOT NULL,
    security_deposit NUMERIC(19,4),
    contract_evidence_id UUID,
    external_contract_reference VARCHAR(255),
    source_type VARCHAR(80) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_by UUID,
    activated_at TIMESTAMPTZ,
    terminated_by UUID,
    terminated_at TIMESTAMPTZ,
    termination_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_lease_property_workspace
        FOREIGN KEY (property_id, workspace_id)
        REFERENCES property.asset(id, workspace_id),
    CONSTRAINT fk_lease_evidence_workspace
        FOREIGN KEY (contract_evidence_id, workspace_id)
        REFERENCES docs.evidence(id, workspace_id),
    CONSTRAINT chk_lease_status CHECK (
        status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED','CANCELLED')
    ),
    CONSTRAINT chk_lease_type CHECK (
        lease_type IN ('RESIDENTIAL','COMMERCIAL','INDUSTRIAL','LAND','OTHER')
    ),
    CONSTRAINT chk_lease_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_lease_rent CHECK (rent_amount > 0),
    CONSTRAINT chk_lease_deposit CHECK (
        security_deposit IS NULL OR security_deposit >= 0
    ),
    CONSTRAINT chk_lease_payment_frequency CHECK (
        payment_frequency IN ('MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL','ONE_TIME')
    ),
    CONSTRAINT chk_active_lease_evidence CHECK (
        status <> 'ACTIVE'
        OR (
            contract_evidence_id IS NOT NULL
            AND activated_by IS NOT NULL
            AND activated_at IS NOT NULL
        )
    ),
    CONSTRAINT chk_terminated_lease_fields CHECK (
        status <> 'TERMINATED'
        OR (
            terminated_by IS NOT NULL
            AND terminated_at IS NOT NULL
            AND termination_reason IS NOT NULL
        )
    )
);

CREATE INDEX idx_lease_property_status
    ON leasing.lease(workspace_id, property_id, status, starts_at, ends_at);

CREATE TABLE leasing.occupancy_period (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL,
    lease_id UUID NOT NULL UNIQUE REFERENCES leasing.lease(id),
    occupant_party_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    occupancy_range TSTZRANGE GENERATED ALWAYS AS (
        tstzrange(starts_at, ends_at, '[)')
    ) STORED,

    CONSTRAINT fk_occupancy_property_workspace
        FOREIGN KEY (property_id, workspace_id)
        REFERENCES property.asset(id, workspace_id),
    CONSTRAINT chk_occupancy_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT chk_occupancy_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_closed_occupancy_time CHECK (
        status <> 'CLOSED' OR closed_at IS NOT NULL
    ),
    CONSTRAINT ex_open_property_occupancy_overlap
        EXCLUDE USING gist (
            property_id WITH =,
            occupancy_range WITH &&
        )
        WHERE (status = 'OPEN')
);

CREATE INDEX idx_occupancy_current
    ON leasing.occupancy_period(workspace_id, property_id, status, starts_at, ends_at);

CREATE OR REPLACE FUNCTION leasing.reject_active_lease_term_rewrite()
RETURNS trigger AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND (
        NEW.property_id IS DISTINCT FROM OLD.property_id
        OR NEW.landlord_party_id IS DISTINCT FROM OLD.landlord_party_id
        OR NEW.tenant_party_id IS DISTINCT FROM OLD.tenant_party_id
        OR NEW.lease_type IS DISTINCT FROM OLD.lease_type
        OR NEW.starts_at IS DISTINCT FROM OLD.starts_at
        OR NEW.ends_at IS DISTINCT FROM OLD.ends_at
        OR NEW.rent_amount IS DISTINCT FROM OLD.rent_amount
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.payment_frequency IS DISTINCT FROM OLD.payment_frequency
        OR NEW.security_deposit IS DISTINCT FROM OLD.security_deposit
        OR NEW.contract_evidence_id IS DISTINCT FROM OLD.contract_evidence_id
        OR NEW.external_contract_reference IS DISTINCT FROM OLD.external_contract_reference
        OR NEW.source_type IS DISTINCT FROM OLD.source_type
    ) THEN
        RAISE EXCEPTION 'activated lease terms are immutable; terminate and create a new lease instead';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lease_terms_immutable_after_draft
BEFORE UPDATE ON leasing.lease
FOR EACH ROW EXECUTE FUNCTION leasing.reject_active_lease_term_rewrite();

COMMENT ON TABLE leasing.lease IS
    'Canonical operational lease lifecycle. A lease record is not merely a document and requires verified evidence before activation.';
COMMENT ON TABLE leasing.occupancy_period IS
    'Canonical temporal occupancy created from an activated lease. Overlapping OPEN occupancy on the same property is forbidden.';
