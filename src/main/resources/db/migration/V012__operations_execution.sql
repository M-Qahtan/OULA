CREATE TABLE ops.work_order (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    action_item_id UUID NOT NULL REFERENCES ops.action_item(id),
    category VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    scope_description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_party_id UUID,
    estimated_cost NUMERIC(19,4) NOT NULL,
    approved_budget NUMERIC(19,4),
    actual_cost NUMERIC(19,4),
    currency CHAR(3) NOT NULL,
    approval_actor_id UUID,
    approved_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completion_submitted_at TIMESTAMPTZ,
    completion_evidence_id UUID,
    completion_note TEXT,
    completed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_work_order_status CHECK (
        status IN (
            'PENDING_APPROVAL','APPROVED','ASSIGNED','IN_PROGRESS',
            'COMPLETION_REVIEW','COMPLETED','CANCELLED'
        )
    ),
    CONSTRAINT chk_work_order_estimated_cost CHECK (estimated_cost >= 0),
    CONSTRAINT chk_work_order_approved_budget CHECK (
        approved_budget IS NULL OR approved_budget >= 0
    ),
    CONSTRAINT chk_work_order_actual_cost CHECK (
        actual_cost IS NULL OR actual_cost >= 0
    )
);

CREATE UNIQUE INDEX uq_active_work_order_action
    ON ops.work_order(action_item_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_work_order_property_status
    ON ops.work_order(workspace_id, property_id, status, created_at DESC);

COMMENT ON TABLE ops.work_order IS
    'Human-authorized operational execution record derived from a Property Guardian action. It is not a payment record and does not autonomously execute external work.';
