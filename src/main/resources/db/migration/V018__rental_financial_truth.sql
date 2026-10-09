-- Wave 16: Evidence-backed rent observations, not a bank ledger.
-- Original rent schedule (V017) remains canonical contract truth and immutable in this wave.
ALTER TABLE tenancy.rent_installment
  ADD CONSTRAINT uq_installment_workspace_lease_id UNIQUE (workspace_id, lease_id, id);

CREATE TABLE tenancy.rent_evidence_entry (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    lease_id UUID NOT NULL,
    installment_id UUID NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    evidence_id UUID NOT NULL UNIQUE,
    external_reference VARCHAR(255),
    reverses_entry_id UUID,
    note VARCHAR(1000) NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (workspace_id, lease_id, installment_id, id),
    FOREIGN KEY (workspace_id, lease_id, installment_id)
        REFERENCES tenancy.rent_installment (workspace_id, lease_id, id),
    FOREIGN KEY (workspace_id, lease_id, installment_id, reverses_entry_id)
        REFERENCES tenancy.rent_evidence_entry (workspace_id, lease_id, installment_id, id),
    CONSTRAINT chk_rent_entry_type CHECK (entry_type IN ('RECEIPT','REVERSAL')),
    CONSTRAINT chk_rent_entry_positive_amount CHECK (amount > 0),
    CONSTRAINT chk_rent_entry_reversal_shape CHECK (
        (entry_type = 'RECEIPT' AND reverses_entry_id IS NULL)
        OR (entry_type = 'REVERSAL' AND reverses_entry_id IS NOT NULL)
    ),
    CONSTRAINT chk_rent_entry_note CHECK (length(trim(note)) > 0)
);

CREATE UNIQUE INDEX uq_rent_entry_external_reference
    ON tenancy.rent_evidence_entry (workspace_id, external_reference)
    WHERE external_reference IS NOT NULL;

CREATE UNIQUE INDEX uq_rent_entry_once_reversal
    ON tenancy.rent_evidence_entry (reverses_entry_id)
    WHERE reverses_entry_id IS NOT NULL;

CREATE INDEX idx_rent_entry_installment_timeline
    ON tenancy.rent_evidence_entry (workspace_id, lease_id, installment_id, recorded_at);

CREATE OR REPLACE FUNCTION tenancy.reject_rent_evidence_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'rent evidence entries are append-only: record a verified reversal';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rent_evidence_immutable
BEFORE UPDATE OR DELETE ON tenancy.rent_evidence_entry
FOR EACH ROW EXECUTE FUNCTION tenancy.reject_rent_evidence_mutation();

COMMENT ON TABLE tenancy.rent_evidence_entry IS
    'Human-attested verified documentary observations only. Not bank confirmation or a funds movement. Corrections require append-only full reversal.';
