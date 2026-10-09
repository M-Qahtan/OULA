CREATE SCHEMA IF NOT EXISTS interventions;

-- Compound identity guarantees that neither snapshot nor Work Order may be
-- attached to a review in another workspace or for another property.
CREATE UNIQUE INDEX uq_vital_snapshot_scoped_id
    ON vitals.property_vital_snapshot (workspace_id, property_id, id);
CREATE UNIQUE INDEX uq_work_order_scoped_id
    ON ops.work_order (workspace_id, property_id, id);

CREATE TABLE interventions.review (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL,
    source_snapshot_id UUID NOT NULL,
    source_policy_key VARCHAR(120) NOT NULL,
    source_policy_version VARCHAR(40) NOT NULL,
    advisory_rules_version VARCHAR(80) NOT NULL,
    dimension VARCHAR(40) NOT NULL,
    action_code VARCHAR(100) NOT NULL,
    baseline_status VARCHAR(16) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    rationale VARCHAR(2000) NOT NULL,
    reviewed_by UUID NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, property_id, id),
    UNIQUE(workspace_id, property_id, source_snapshot_id, dimension, action_code),
    FOREIGN KEY(workspace_id, property_id, source_snapshot_id)
        REFERENCES vitals.property_vital_snapshot(workspace_id, property_id, id),
    CONSTRAINT chk_intervention_review_decision
        CHECK(decision IN ('ACKNOWLEDGED', 'DECLINED', 'DEFERRED')),
    CONSTRAINT chk_intervention_review_status
        CHECK(baseline_status IN ('GREEN','AMBER','RED','UNKNOWN')),
    CONSTRAINT chk_intervention_review_rationale CHECK (length(btrim(rationale)) > 0)
);

CREATE TABLE interventions.outcome_observation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES iam.workspace(id),
    property_id UUID NOT NULL,
    review_id UUID NOT NULL UNIQUE,
    after_snapshot_id UUID NOT NULL,
    work_order_id UUID,
    before_status VARCHAR(16) NOT NULL,
    after_status VARCHAR(16) NOT NULL,
    observed_direction VARCHAR(24) NOT NULL,
    execution_evidence_level VARCHAR(32) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    observed_by UUID NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(workspace_id, property_id, review_id)
        REFERENCES interventions.review(workspace_id, property_id, id),
    FOREIGN KEY(workspace_id, property_id, after_snapshot_id)
        REFERENCES vitals.property_vital_snapshot(workspace_id, property_id, id),
    FOREIGN KEY(workspace_id, property_id, work_order_id)
        REFERENCES ops.work_order(workspace_id, property_id, id),
    CONSTRAINT chk_intervention_direction CHECK(
        observed_direction IN ('IMPROVED','WORSENED','UNCHANGED','NOT_COMPARABLE')),
    CONSTRAINT chk_intervention_evidence_level CHECK(
        execution_evidence_level IN ('OBSERVATION_ONLY','VERIFIED_WORK_ORDER')),
    CONSTRAINT chk_intervention_evidence_shape CHECK(
        (execution_evidence_level = 'OBSERVATION_ONLY' AND work_order_id IS NULL)
        OR (execution_evidence_level = 'VERIFIED_WORK_ORDER' AND work_order_id IS NOT NULL)),
    CONSTRAINT chk_intervention_before_status CHECK(before_status IN ('GREEN','AMBER','RED','UNKNOWN')),
    CONSTRAINT chk_intervention_after_status CHECK(after_status IN ('GREEN','AMBER','RED','UNKNOWN')),
    CONSTRAINT chk_intervention_outcome_note CHECK (length(btrim(note)) > 0)
);
CREATE INDEX idx_intervention_review_property
    ON interventions.review(workspace_id,property_id,reviewed_at DESC);
CREATE INDEX idx_intervention_outcome_property
    ON interventions.outcome_observation(workspace_id,property_id,observed_at DESC);

CREATE OR REPLACE FUNCTION interventions.reject_history_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'intervention reviews and outcomes are append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_intervention_review_immutable
BEFORE UPDATE OR DELETE ON interventions.review
FOR EACH ROW EXECUTE FUNCTION interventions.reject_history_mutation();
CREATE TRIGGER trg_intervention_outcome_immutable
BEFORE UPDATE OR DELETE ON interventions.outcome_observation
FOR EACH ROW EXECUTE FUNCTION interventions.reject_history_mutation();

COMMENT ON TABLE interventions.review IS
    'Human assessment of a versioned advisory proposal, not financial or execution authority.';
COMMENT ON TABLE interventions.outcome_observation IS
    'Observed after-state and optional independently verified Work Order reference. Does not prove causality.';
