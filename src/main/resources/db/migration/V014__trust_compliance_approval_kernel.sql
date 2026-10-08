CREATE TABLE compliance.policy_rule (
    id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES iam.workspace(id),
    policy_key VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    applies_purpose VARCHAR(64) NOT NULL,
    action_pattern VARCHAR(120) NOT NULL,
    resource_type_pattern VARCHAR(80) NOT NULL,
    jurisdiction_pattern VARCHAR(80) NOT NULL DEFAULT '*',
    rule_effect VARCHAR(40) NOT NULL,
    max_amount NUMERIC(19,4),
    currency CHAR(3),
    requires_verification BOOLEAN NOT NULL DEFAULT FALSE,
    requires_evidence BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_until TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_policy_rule_effect CHECK (
        rule_effect IN (
            'ALLOW','DENY','REQUIRE_APPROVAL','REQUIRE_DOCUMENT',
            'REQUIRE_VERIFICATION','ESCALATE'
        )
    ),
    CONSTRAINT chk_policy_rule_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_policy_rule_max_amount CHECK (
        max_amount IS NULL OR max_amount >= 0
    )
);

CREATE UNIQUE INDEX uq_policy_rule_version
    ON compliance.policy_rule (
        coalesce(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid),
        policy_key,
        version
    );

CREATE INDEX idx_policy_rule_match
    ON compliance.policy_rule (
        status, applies_purpose, action_pattern, resource_type_pattern,
        jurisdiction_pattern, priority DESC
    );

CREATE TABLE compliance.approval_request (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    policy_rule_id UUID NOT NULL REFERENCES compliance.policy_rule(id),
    policy_decision_id UUID REFERENCES compliance.policy_decision(id),
    requested_by UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    jurisdiction VARCHAR(80) NOT NULL,
    amount NUMERIC(19,4),
    currency CHAR(3),
    justification TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_approval_request_status CHECK (
        status IN ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED')
    ),
    CONSTRAINT chk_approval_request_amount CHECK (
        amount IS NULL OR amount >= 0
    ),
    CONSTRAINT chk_approval_request_decision_fields CHECK (
        status = 'PENDING'
        OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR status IN ('CANCELLED','EXPIRED')
    )
);

CREATE INDEX idx_approval_request_workspace_status
    ON compliance.approval_request(workspace_id, status, requested_at DESC);

ALTER TABLE compliance.policy_decision
    ADD COLUMN purpose VARCHAR(64),
    ADD COLUMN jurisdiction VARCHAR(80),
    ADD COLUMN amount NUMERIC(19,4),
    ADD COLUMN currency CHAR(3),
    ADD COLUMN policy_rule_id UUID REFERENCES compliance.policy_rule(id),
    ADD COLUMN approval_request_id UUID REFERENCES compliance.approval_request(id),
    ADD COLUMN context JSONB NOT NULL DEFAULT '{}'::jsonb;

INSERT INTO compliance.policy_rule (
    id, workspace_id, policy_key, version, applies_purpose,
    action_pattern, resource_type_pattern, jurisdiction_pattern,
    rule_effect, priority, status, created_at
) VALUES
(
    '00000000-0000-7000-8000-000000000901', NULL,
    'SYSTEM_PROPERTY_MANAGEMENT_BASELINE', '1',
    'PROPERTY_MANAGEMENT', '*', '*', '*',
    'ALLOW', 10, 'ACTIVE', now()
),
(
    '00000000-0000-7000-8000-000000000902', NULL,
    'SYSTEM_TRANSACTION_EXECUTION_BASELINE', '1',
    'TRANSACTION_EXECUTION', '*', '*', '*',
    'ALLOW', 10, 'ACTIVE', now()
),
(
    '00000000-0000-7000-8000-000000000903', NULL,
    'SYSTEM_DECISION_SUPPORT_BASELINE', '1',
    'PROPERTY_DECISION_SUPPORT', '*', '*', '*',
    'ALLOW', 10, 'ACTIVE', now()
),
(
    '00000000-0000-7000-8000-000000000904', NULL,
    'SYSTEM_AUTONOMOUS_EXECUTION_BASELINE', '1',
    'AUTONOMOUS_EXECUTION', '*', '*', '*',
    'REQUIRE_APPROVAL', 10, 'ACTIVE', now()
);

COMMENT ON TABLE compliance.policy_rule IS
    'Deterministic OULA authorization/compliance rules. Workspace rules override system rules by specificity and priority.';
COMMENT ON TABLE compliance.approval_request IS
    'Human approval lifecycle for policy decisions that cannot be autonomously executed.';
COMMENT ON COLUMN compliance.policy_rule.action_pattern IS
    'Exact action name or * only. No executable expressions are stored.';
