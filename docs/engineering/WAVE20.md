# OULA Wave 20 — Reality Timeline & Decision Replay Read Model

## Purpose

Expose the existing Reality Memory chain as a reproducible, chronological read-only view for the Decision Workspace. This is **not** a new facts table, event ledger, model-training input, or cross-tenant analytics store.

`GET /v1/intelligence/outcomes/{outcomeId}/timeline`

Requires:
- OIDC/OAuth2 workspace membership and `oula.intelligence.read`;
- `X-OULA-Purpose: PROPERTY_DECISION_SUPPORT`;
- response `Cache-Control: no-store`.

## Canonical temporal lineage

1. `property.state_snapshot`: only the snapshot that was **effective AND recorded before the human decision**, identified through the canonical `v_reality_case` projection;
2. `intelligence.recommendation`: the model proposal, never a human choice;
3. `intelligence.decision_record`: the recorded human choice or override;
4. `intelligence.outcome`: recorded outcome, not necessarily independent verification;
5. `intelligence.reality_gap`: measured difference, **not** causal attribution;
6. `intelligence.error_hypothesis_review`: reviewed human hypothesis, **not** established causal proof.

Items contain event kind, source UUID, recorded timestamp, epistemic class and source-owned status. No raw property-state JSON, PII, supporting document bytes, hypothetical causes or unapproved cross-workspace data are copied into the response.

## Engineering constraints

- Current PostgreSQL 16/PostGIS and Flyway V001–V020 unchanged; this is a read-only projection and adds **no migration**.
- Spring Modulith `intelligence` owns this composition; `property`, `transaction`, `tenancy` and `advisory` retain their own canonical truth.
- All source joins remain scoped to the authorized workspace and outcome.
- The `RealityCase` lookup fails closed for missing/foreign outcomes.
- All timeline records are sourced from persisted canonical tables, ordered by recorded event time, then deterministic stage/source order.
- No autonomous action, calibration, causal inference, financing, contract signing or payment is performed.

## Testable exit criteria

- Replayed human-reviewed outcome includes recommendation, decision, outcome, gap and review in chronological order.
- Decision-time property memory **includes** earlier known state, while excluding retrospectively recorded state even if its effective date was earlier.
- Foreign workspace cannot read a RealityCase/Timeline.
- CI: Java 21, Spring Modulith boundary checks, PostgreSQL/PostGIS integration suite and CodeQL pass on the final head.

## Future UI

The Decision Workspace may render the read model as a source-linked timeline, clearly distinguishing **known at decision** from **observed after decision**. Separate domain-owned read projections will be required before combining property operations or rental review events. Do not silently pool data across tenant or purpose boundaries.
