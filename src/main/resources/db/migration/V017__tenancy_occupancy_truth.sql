CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS tenancy;

-- Compound ownership keys prevent cross-workspace references, including SQL callers.
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_workspace_id ON property.asset(workspace_id, id);

CREATE TABLE tenancy.unit (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    property_id UUID NOT NULL,
    unit_code VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, property_id, unit_code),
    FOREIGN KEY(workspace_id, property_id) REFERENCES property.asset(workspace_id, id),
    CONSTRAINT chk_unit_status CHECK(status IN ('ACTIVE','RETIRED'))
);

CREATE TABLE tenancy.lease (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    landlord_party_id UUID NOT NULL,
    tenant_party_id UUID NOT NULL,
    start_on DATE NOT NULL,
    end_on DATE NOT NULL,
    periodic_rent NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    rent_every_months SMALLINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    signing_evidence_id UUID,
    signed_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    termination_evidence_id UUID,
    ended_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, id, unit_id),
    FOREIGN KEY(workspace_id, unit_id) REFERENCES tenancy.unit(workspace_id, id),
    CONSTRAINT chk_lease_period CHECK(end_on > start_on),
    CONSTRAINT chk_lease_rent CHECK(periodic_rent > 0),
    CONSTRAINT chk_lease_frequency CHECK(rent_every_months IN (1,3,12)),
    CONSTRAINT chk_lease_status CHECK(status IN ('DRAFT','SIGNED','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT chk_lease_signed_evidence CHECK(
      status NOT IN ('SIGNED','ACTIVE','ENDED')
      OR (signing_evidence_id IS NOT NULL AND signed_at IS NOT NULL)
    ),
    CONSTRAINT chk_lease_ended_evidence CHECK(
      status <> 'ENDED' OR (termination_evidence_id IS NOT NULL AND ended_at IS NOT NULL)
    ),
    CONSTRAINT chk_distinct_parties CHECK(landlord_party_id <> tenant_party_id),
    CONSTRAINT ex_lease_period_no_overlap EXCLUDE USING gist (
      unit_id WITH =,
      daterange(start_on, end_on, '[)') WITH &&
    ) WHERE (status IN ('SIGNED','ACTIVE'))
);

CREATE TABLE tenancy.rent_installment (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    lease_id UUID NOT NULL,
    due_on DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(workspace_id, lease_id) REFERENCES tenancy.lease(workspace_id, id),
    UNIQUE(lease_id, due_on),
    CONSTRAINT chk_rent_installment_amount CHECK(amount > 0),
    CONSTRAINT chk_rent_installment_status CHECK(status = 'SCHEDULED')
);
COMMENT ON TABLE tenancy.rent_installment IS
  'Contractual rent schedule only, not receipt, funds movement, or proof of payment.';

CREATE TABLE tenancy.occupancy (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    lease_id UUID NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL,
    check_in_evidence_id UUID NOT NULL,
    checked_out_at TIMESTAMPTZ,
    check_out_evidence_id UUID,
    recorded_by UUID NOT NULL,
    FOREIGN KEY(workspace_id, unit_id) REFERENCES tenancy.unit(workspace_id, id),
    FOREIGN KEY(workspace_id, lease_id, unit_id) REFERENCES tenancy.lease(workspace_id, id, unit_id),
    CONSTRAINT chk_checkout_evidence CHECK(
      (checked_out_at IS NULL AND check_out_evidence_id IS NULL)
      OR (checked_out_at IS NOT NULL AND check_out_evidence_id IS NOT NULL
          AND checked_out_at >= checked_in_at)
    ),
    CONSTRAINT uq_occupancy_lease UNIQUE(lease_id)
);
CREATE UNIQUE INDEX uq_current_occupancy_unit
    ON tenancy.occupancy(unit_id) WHERE checked_out_at IS NULL;

CREATE TABLE tenancy.renewal_decision (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    lease_id UUID NOT NULL,
    decision VARCHAR(24) NOT NULL,
    rationale VARCHAR(2000) NOT NULL,
    evidence_id UUID,
    actor_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(workspace_id, lease_id) REFERENCES tenancy.lease(workspace_id, id),
    CONSTRAINT chk_renewal_decision CHECK(
        decision IN ('RENEWAL_REQUESTED','RENEWAL_DECLINED','RENEWAL_ACCEPTED','NEEDS_REVIEW')
    )
);
CREATE INDEX idx_tenancy_lease_unit ON tenancy.lease(workspace_id,unit_id,status,start_on,end_on);
CREATE INDEX idx_tenancy_rent_due ON tenancy.rent_installment(workspace_id,lease_id,due_on);
CREATE INDEX idx_tenancy_renewal_lease ON tenancy.renewal_decision(workspace_id,lease_id,recorded_at DESC);
COMMENT ON TABLE tenancy.lease IS
  'Evidence-backed internal contract lifecycle record; not government registration, legal signing or title verification.';
COMMENT ON TABLE tenancy.occupancy IS
  'Evidence-backed physical possession/occupancy, distinct from a signed lease or reported rent.';

CREATE OR REPLACE FUNCTION tenancy.reject_renewal_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'renewal decisions are append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_renewal_decision_immutable
BEFORE UPDATE OR DELETE ON tenancy.renewal_decision
FOR EACH ROW EXECUTE FUNCTION tenancy.reject_renewal_mutation();
