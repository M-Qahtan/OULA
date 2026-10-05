CREATE UNIQUE INDEX IF NOT EXISTS uq_match_run_workspace_correlation
    ON matching.match_run (workspace_id, correlation_id);

CREATE INDEX IF NOT EXISTS idx_match_result_rank
    ON matching.match_result (match_run_id, rank);

CREATE INDEX IF NOT EXISTS idx_tx_transaction_workspace_stage
    ON tx.transaction (workspace_id, stage);
