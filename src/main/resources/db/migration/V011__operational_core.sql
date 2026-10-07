CREATE TABLE people.household (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    name VARCHAR(200),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_people_household_status CHECK (
        status IN ('ACTIVE','ARCHIVED')
    )
);

CREATE TABLE people.household_member (
    household_id UUID NOT NULL REFERENCES people.household(id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people.person(id) ON DELETE CASCADE,
    relationship VARCHAR(64) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    PRIMARY KEY(household_id, person_id)
);

CREATE TABLE people.life_profile_snapshot (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    person_id UUID NOT NULL REFERENCES people.person(id),
    household_id UUID REFERENCES people.household(id),
    version INTEGER NOT NULL,
    household_size INTEGER NOT NULL,
    max_housing_budget NUMERIC(18,2) NOT NULL,
    mobility_anchor_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    effective_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    supersedes_snapshot_id UUID REFERENCES people.life_profile_snapshot(id),
    correlation_id UUID NOT NULL,
    CONSTRAINT uq_life_profile_snapshot_version UNIQUE(person_id, version),
    CONSTRAINT chk_life_profile_household_size CHECK (household_size > 0),
    CONSTRAINT chk_life_profile_budget CHECK (max_housing_budget > 0)
);

CREATE INDEX idx_life_profile_snapshot_latest
    ON people.life_profile_snapshot(workspace_id, person_id, effective_at DESC, version DESC);

CREATE OR REPLACE FUNCTION people.reject_life_profile_snapshot_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'people.life_profile_snapshot is immutable; record a new version instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_life_profile_snapshot_immutable
BEFORE UPDATE OR DELETE ON people.life_profile_snapshot
FOR EACH ROW EXECUTE FUNCTION people.reject_life_profile_snapshot_mutation();

CREATE UNIQUE INDEX uq_people_person_subject
    ON people.person(workspace_id, linked_subject)
    WHERE linked_subject IS NOT NULL AND status = 'ACTIVE';

ALTER TABLE intent.intent
    ADD COLUMN created_by UUID,
    ADD COLUMN activated_at TIMESTAMPTZ;

ALTER TABLE intent.intent
    ADD CONSTRAINT chk_intent_status
    CHECK (status IN ('DRAFT','ACTIVE','PAUSED','FULFILLED','CANCELLED'));

ALTER TABLE docs.evidence
    ADD COLUMN verified_by UUID,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN verification_reason TEXT;

ALTER TABLE docs.evidence
    ADD CONSTRAINT chk_docs_evidence_verification_status
    CHECK (verification_status IN ('RECEIVED','VERIFIED','REJECTED','EXPIRED'));

CREATE INDEX idx_docs_evidence_workspace_status
    ON docs.evidence(workspace_id, verification_status, captured_at DESC);

ALTER TABLE property.fact
    ADD COLUMN visibility VARCHAR(24) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN evidence_reference UUID,
    ADD COLUMN recorded_by UUID,
    ADD COLUMN verified_by UUID,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE property.fact
    ADD CONSTRAINT chk_property_fact_visibility
    CHECK (visibility IN ('PUBLIC','WORKSPACE','RESTRICTED')),
    ADD CONSTRAINT chk_property_fact_truth_status
    CHECK (truth_status IN (
        'VERIFIED','DECLARED','OBSERVED','CALCULATED','ESTIMATED',
        'AI_INFERRED','DISPUTED','EXPIRED'
    )),
    ADD CONSTRAINT chk_verified_fact_lineage
    CHECK (
        truth_status <> 'VERIFIED'
        OR (
            evidence_reference IS NOT NULL
            AND verified_by IS NOT NULL
            AND verified_at IS NOT NULL
        )
    ) NOT VALID;

CREATE INDEX idx_property_fact_public
    ON property.fact(property_id, truth_status, fact_key)
    WHERE visibility = 'PUBLIC';

ALTER TABLE market.listing
    ADD COLUMN created_by UUID,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE market.listing
    ADD CONSTRAINT chk_market_listing_status
    CHECK (status IN ('DRAFT','PUBLISHED','WITHDRAWN','EXPIRED')),
    ADD CONSTRAINT chk_market_listing_transaction_type
    CHECK (transaction_type IN ('SALE','RENT'));

CREATE UNIQUE INDEX uq_market_published_listing
    ON market.listing(property_id, transaction_type)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_market_published_supply
    ON market.listing(transaction_type, asking_price, published_at DESC)
    WHERE status = 'PUBLISHED';

ALTER TABLE tx.transaction
    ADD COLUMN origin_decision_id UUID,
    ADD COLUMN opened_by UUID;

CREATE UNIQUE INDEX uq_tx_origin_decision
    ON tx.transaction(workspace_id, origin_decision_id)
    WHERE origin_decision_id IS NOT NULL;

COMMENT ON COLUMN property.fact.evidence_reference IS
    'Opaque evidence identifier validated by an authorized evidence boundary before VERIFIED promotion.';
COMMENT ON COLUMN tx.transaction.origin_decision_id IS
    'Opaque intelligence DecisionRecord identifier. Orchestration validates lineage before transaction creation.';
