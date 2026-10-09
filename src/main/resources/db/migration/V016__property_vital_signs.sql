CREATE SCHEMA IF NOT EXISTS vitals;

CREATE TABLE vitals.policy (
    policy_key VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    cost_utilization_amber NUMERIC(8,6) NOT NULL,
    cost_utilization_red NUMERIC(8,6) NOT NULL,
    provider_rating_amber NUMERIC(4,2) NOT NULL,
    provider_rating_red NUMERIC(4,2) NOT NULL,
    provider_cost_variance_amber NUMERIC(8,6) NOT NULL,
    provider_cost_variance_red NUMERIC(8,6) NOT NULL,
    truth_coverage_amber NUMERIC(8,6) NOT NULL,
    truth_coverage_red NUMERIC(8,6) NOT NULL,
    freshness_amber_days INTEGER NOT NULL,
    freshness_red_days INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    PRIMARY KEY (policy_key, version),
    CONSTRAINT chk_vitals_policy_cost CHECK (
        cost_utilization_amber >= 0
        AND cost_utilization_red >= cost_utilization_amber
    ),
    CONSTRAINT chk_vitals_policy_provider_rating CHECK (
        provider_rating_red >= 0
        AND provider_rating_amber >= provider_rating_red
        AND provider_rating_amber <= 5
    ),
    CONSTRAINT chk_vitals_policy_provider_variance CHECK (
        provider_cost_variance_amber >= 0
        AND provider_cost_variance_red >= provider_cost_variance_amber
    ),
    CONSTRAINT chk_vitals_policy_truth CHECK (
        truth_coverage_red >= 0
        AND truth_coverage_amber >= truth_coverage_red
        AND truth_coverage_amber <= 1
    ),
    CONSTRAINT chk_vitals_policy_freshness CHECK (
        freshness_amber_days >= 0
        AND freshness_red_days >= freshness_amber_days
    ),
    CONSTRAINT chk_vitals_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
);

INSERT INTO vitals.policy (
    policy_key, version,
    cost_utilization_amber, cost_utilization_red,
    provider_rating_amber, provider_rating_red,
    provider_cost_variance_amber, provider_cost_variance_red,
    truth_coverage_amber, truth_coverage_red,
    freshness_amber_days, freshness_red_days,
    status, effective_from
) VALUES (
    'property-operational-vitals', 'v1',
    0.900000, 1.000000,
    3.50, 2.50,
    0.050000, 0.150000,
    0.750000, 0.400000,
    30, 90,
    'ACTIVE', now()
);

CREATE TABLE vitals.property_vital_snapshot (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL REFERENCES property.asset(id),
    policy_key VARCHAR(120) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,

    overall_status VARCHAR(16) NOT NULL,
    obligation_status VARCHAR(16) NOT NULL,
    guardian_status VARCHAR(16) NOT NULL,
    execution_status VARCHAR(16) NOT NULL,
    cost_status VARCHAR(16) NOT NULL,
    provider_status VARCHAR(16) NOT NULL,
    evidence_status VARCHAR(16) NOT NULL,
    truth_status VARCHAR(16) NOT NULL,
    freshness_status VARCHAR(16) NOT NULL,

    open_obligations INTEGER NOT NULL,
    overdue_obligations INTEGER NOT NULL,
    open_guardian_signals INTEGER NOT NULL,
    critical_guardian_signals INTEGER NOT NULL,
    open_work_orders INTEGER NOT NULL,
    overdue_work_orders INTEGER NOT NULL,
    completed_work_orders INTEGER NOT NULL,
    completion_review_work_orders INTEGER NOT NULL,
    missing_completion_evidence INTEGER NOT NULL,

    average_budget_utilization NUMERIC(12,8),
    average_provider_rating NUMERIC(6,4),
    average_provider_cost_variance_ratio NUMERIC(12,8),
    verified_fact_coverage NUMERIC(12,8),

    latest_operational_activity_at TIMESTAMPTZ,
    latest_property_fact_at TIMESTAMPTZ,

    assessed_by UUID NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_property_vitals_policy
        FOREIGN KEY (policy_key, policy_version)
        REFERENCES vitals.policy(policy_key, version),
    CONSTRAINT chk_property_vitals_status CHECK (
        overall_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND obligation_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND guardian_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND execution_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND cost_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND provider_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND evidence_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND truth_status IN ('GREEN','AMBER','RED','UNKNOWN')
        AND freshness_status IN ('GREEN','AMBER','RED','UNKNOWN')
    ),
    CONSTRAINT chk_property_vitals_counts CHECK (
        open_obligations >= 0
        AND overdue_obligations >= 0
        AND open_guardian_signals >= 0
        AND critical_guardian_signals >= 0
        AND open_work_orders >= 0
        AND overdue_work_orders >= 0
        AND completed_work_orders >= 0
        AND completion_review_work_orders >= 0
        AND missing_completion_evidence >= 0
    )
);

CREATE INDEX idx_property_vitals_latest
    ON vitals.property_vital_snapshot(workspace_id, property_id, assessed_at DESC);

CREATE OR REPLACE FUNCTION vitals.reject_snapshot_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'vitals.property_vital_snapshot is immutable; record a new assessment instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_property_vital_snapshot_immutable
BEFORE UPDATE OR DELETE ON vitals.property_vital_snapshot
FOR EACH ROW EXECUTE FUNCTION vitals.reject_snapshot_mutation();

COMMENT ON TABLE vitals.property_vital_snapshot IS
    'Immutable deterministic property operational-health observation. It is a derived projection over canonical domain truth, not a second source of property truth.';
COMMENT ON TABLE vitals.policy IS
    'Versioned deterministic thresholds for Property Vital Signs. Policy changes create a new version rather than rewriting historical interpretation.';
