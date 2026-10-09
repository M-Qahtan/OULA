CREATE SCHEMA IF NOT EXISTS advisory;
CREATE TABLE advisory.review_case (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    property_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    lease_id UUID,
    rules_version VARCHAR(80) NOT NULL,
    dimension VARCHAR(80) NOT NULL,
    action_code VARCHAR(120) NOT NULL,
    initial_priority VARCHAR(24) NOT NULL,
    source_generated_at TIMESTAMPTZ NOT NULL,
    source_metrics JSONB NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    captured_by UUID NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    UNIQUE(workspace_id,id),
    UNIQUE(workspace_id,property_id,source_fingerprint),
    FOREIGN KEY(workspace_id,property_id) REFERENCES property.asset(workspace_id,id),
    CONSTRAINT chk_case_priority CHECK(initial_priority IN ('HIGH','MEDIUM','DATA_QUALITY')),
    CONSTRAINT chk_case_metrics CHECK(jsonb_typeof(source_metrics) = 'array')
);
CREATE INDEX idx_review_case_property
    ON advisory.review_case(workspace_id,property_id,captured_at DESC);

CREATE TABLE advisory.review_event (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    review_case_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    value_code VARCHAR(48) NOT NULL,
    rationale VARCHAR(2000) NOT NULL,
    evidence_id UUID,
    actor_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(workspace_id,review_case_id)
        REFERENCES advisory.review_case(workspace_id,id),
    UNIQUE(review_case_id,sequence_no),
    CONSTRAINT chk_review_event_type CHECK(event_type IN ('DECISION','OUTCOME_OBSERVATION')),
    CONSTRAINT chk_review_event_decision CHECK(
        (event_type='DECISION'
          AND value_code IN ('ACCEPT_FOR_REVIEW','DECLINE','DEFER')
          AND evidence_id IS NULL)
        OR
        (event_type='OUTCOME_OBSERVATION'
          AND value_code IN ('IMPROVEMENT_OBSERVED','NO_CHANGE_OBSERVED',
                              'DETERIORATION_OBSERVED','INCONCLUSIVE')
          AND evidence_id IS NOT NULL)
    ),
    CONSTRAINT chk_review_event_note CHECK(length(trim(rationale)) BETWEEN 1 AND 2000)
);
CREATE UNIQUE INDEX uq_advisory_review_evidence
  ON advisory.review_event(workspace_id,evidence_id)
  WHERE evidence_id IS NOT NULL;
CREATE INDEX idx_advisory_review_events
  ON advisory.review_event(workspace_id,review_case_id,sequence_no);

CREATE OR REPLACE FUNCTION advisory.reject_review_history_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'advisory review history is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_advisory_case_immutable
BEFORE UPDATE OR DELETE ON advisory.review_case
FOR EACH ROW EXECUTE FUNCTION advisory.reject_review_history_mutation();
CREATE TRIGGER trg_advisory_event_immutable
BEFORE UPDATE OR DELETE ON advisory.review_event
FOR EACH ROW EXECUTE FUNCTION advisory.reject_review_history_mutation();

COMMENT ON TABLE advisory.review_case IS
'Frozen provenance of one generated rental recommendation, not a verified outcome or authorization.';
COMMENT ON TABLE advisory.review_event IS
'Human decision and document-backed outcome observations only; neither autonomous execution nor causal efficacy proof.';
