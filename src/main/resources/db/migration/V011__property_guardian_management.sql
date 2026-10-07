CREATE TABLE ops.management_enrollment (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    manager_actor_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_management_enrollment_status CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE UNIQUE INDEX uq_active_property_management
    ON ops.management_enrollment(workspace_id, property_id)
    WHERE status = 'ACTIVE';

CREATE TABLE ops.obligation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    obligation_type VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    priority VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_reference VARCHAR(255),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_obligation_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_obligation_status CHECK (status IN ('OPEN','SATISFIED','CANCELLED'))
);

ALTER TABLE ops.guardian_signal
    ADD COLUMN obligation_id UUID REFERENCES ops.obligation(id),
    ADD COLUMN message TEXT,
    ADD COLUMN recommended_action VARCHAR(120),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_guardian_signal_obligation
    ON ops.guardian_signal(obligation_id)
    WHERE obligation_id IS NOT NULL;

CREATE TABLE ops.action_item (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    obligation_id UUID REFERENCES ops.obligation(id),
    guardian_signal_id UUID REFERENCES ops.guardian_signal(id),
    action_type VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    due_at TIMESTAMPTZ,
    assigned_actor_id UUID,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_action_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE UNIQUE INDEX uq_action_obligation_type
    ON ops.action_item(obligation_id, action_type)
    WHERE obligation_id IS NOT NULL;

CREATE INDEX idx_obligation_due
    ON ops.obligation(workspace_id, property_id, status, due_at);

CREATE INDEX idx_action_property_status
    ON ops.action_item(workspace_id, property_id, status, due_at);

CREATE INDEX idx_guardian_signal_open
    ON ops.guardian_signal(workspace_id, property_id, status, detected_at DESC);

COMMENT ON TABLE ops.obligation IS
    'Canonical operational obligation ledger for a managed property.';
COMMENT ON TABLE ops.action_item IS
    'Executable work derived from obligations or Guardian signals; not an AI-owned source of property truth.';
