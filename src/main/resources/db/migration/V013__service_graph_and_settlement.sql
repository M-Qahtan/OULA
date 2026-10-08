CREATE SCHEMA IF NOT EXISTS service_graph;
CREATE SCHEMA IF NOT EXISTS settlement;

CREATE TABLE service_graph.provider (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    provider_party_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    verification_evidence_id UUID,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_provider_party UNIQUE(workspace_id, provider_party_id),
    CONSTRAINT chk_service_provider_status CHECK (status IN ('ACTIVE','SUSPENDED')),
    CONSTRAINT chk_service_provider_verification CHECK (
        verification_status IN ('UNVERIFIED','VERIFIED','EXPIRED')
    ),
    CONSTRAINT chk_verified_provider_has_evidence CHECK (
        verification_status <> 'VERIFIED'
        OR (verification_evidence_id IS NOT NULL AND verified_at IS NOT NULL)
    )
);

CREATE TABLE service_graph.provider_capability (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES service_graph.provider(id) ON DELETE CASCADE,
    category VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_capability UNIQUE(provider_id, category),
    CONSTRAINT chk_provider_capability_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE service_graph.quote (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    work_order_id UUID NOT NULL REFERENCES ops.work_order(id),
    provider_id UUID NOT NULL REFERENCES service_graph.provider(id),
    provider_party_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    lead_time_days INTEGER NOT NULL,
    scope_note TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    selected_by UUID,
    selected_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_service_quote_amount CHECK (amount >= 0),
    CONSTRAINT chk_service_quote_lead_time CHECK (lead_time_days >= 0),
    CONSTRAINT chk_service_quote_status CHECK (
        status IN ('SUBMITTED','SELECTED','REJECTED','WITHDRAWN')
    ),
    CONSTRAINT chk_selected_quote_authority CHECK (
        status <> 'SELECTED'
        OR (selected_by IS NOT NULL AND selected_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_active_quote_provider_work_order
    ON service_graph.quote(work_order_id, provider_id)
    WHERE status IN ('SUBMITTED','SELECTED');

CREATE UNIQUE INDEX uq_selected_quote_work_order
    ON service_graph.quote(work_order_id)
    WHERE status = 'SELECTED';

CREATE INDEX idx_service_quote_work_order
    ON service_graph.quote(workspace_id, work_order_id, status, submitted_at);

CREATE TABLE service_graph.provider_outcome (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    work_order_id UUID NOT NULL REFERENCES ops.work_order(id),
    quote_id UUID NOT NULL REFERENCES service_graph.quote(id),
    provider_id UUID NOT NULL REFERENCES service_graph.provider(id),
    quoted_amount NUMERIC(19,4) NOT NULL,
    actual_cost NUMERIC(19,4) NOT NULL,
    cost_variance NUMERIC(19,4) NOT NULL,
    rating SMALLINT,
    outcome_note TEXT,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_outcome_work_order UNIQUE(work_order_id),
    CONSTRAINT chk_provider_outcome_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE TABLE settlement.reference (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    work_order_id UUID NOT NULL REFERENCES ops.work_order(id),
    provider_party_id UUID NOT NULL,
    processor_code VARCHAR(80) NOT NULL,
    external_reference VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    evidence_id UUID,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_settlement_external_ref UNIQUE(processor_code, external_reference),
    CONSTRAINT chk_settlement_amount CHECK (amount >= 0),
    CONSTRAINT chk_settlement_status CHECK (
        status IN ('PENDING','SETTLED','FAILED','REVERSED')
    ),
    CONSTRAINT chk_settled_reference_evidence CHECK (
        status <> 'SETTLED' OR evidence_id IS NOT NULL
    )
);

CREATE INDEX idx_settlement_work_order
    ON settlement.reference(workspace_id, work_order_id, status, recorded_at DESC);

COMMENT ON TABLE service_graph.provider IS
    'Workspace-scoped operational provider profile. It does not replace canonical legal organization identity.';
COMMENT ON TABLE service_graph.quote IS
    'Provider quote submitted against one OULA work order; human selection is required before assignment.';
COMMENT ON TABLE service_graph.provider_outcome IS
    'Observed provider execution outcome after verified work completion; not an inferred reputation score.';
COMMENT ON TABLE settlement.reference IS
    'Reference to an externally executed settlement. OULA records evidence/status but does not move funds.';
