ALTER TABLE platform.idempotency_record
    ADD COLUMN operation VARCHAR(160) NOT NULL DEFAULT 'legacy',
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS';

ALTER TABLE platform.idempotency_record
    ADD CONSTRAINT chk_idempotency_state
    CHECK (state IN ('IN_PROGRESS', 'COMPLETED'));

CREATE INDEX idx_idempotency_expiry
    ON platform.idempotency_record (expires_at);

ALTER TABLE platform.audit_log
    ADD COLUMN actor_subject VARCHAR(255),
    ADD COLUMN purpose VARCHAR(80);

CREATE TABLE matching.intent_property_signal (
    intent_id UUID NOT NULL REFERENCES intent.intent(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES property.asset(id) ON DELETE CASCADE,
    commute_minutes INTEGER NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    confidence NUMERIC(5,4),
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (intent_id, property_id),
    CHECK (commute_minutes >= 0),
    CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_intent_property_signal_property
    ON matching.intent_property_signal (property_id);
