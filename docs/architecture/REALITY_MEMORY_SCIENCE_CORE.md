# OULA Reality Memory & Scientific Calibration Core — Wave 05

Status: implementation branch.

## Mission

Turn OULA's decision loop into reproducible institutional memory without creating
parallel truth stores or allowing the model to rewrite itself from one error.

The production chain is:

```
Property State Snapshot
  -> Recommendation / Expected State
  -> Human Decision
  -> Outcome / Actual State
  -> Reality Gap
  -> Reviewed Error Hypothesis
  -> Calibration Projection
  -> Validation candidate
```

## Ownership

- `property` owns property identity, facts and immutable temporal state snapshots.
- `intelligence` owns observations, evidence, assumptions, recommendations,
  outcomes, Reality Gaps, review hypotheses and calibration projections.
- `RealityCase` is a composition/read model only.
- no snapshot, RealityCase or calibration view may silently become canonical
  property truth.

## Temporal anti-hindsight invariant

A property snapshot may be attached to a RealityCase only when both
`effective_at <= decided_at` and `recorded_at <= decided_at`.

This prevents OULA from using information learned after a human decision as if it
had been known before that decision.

## Immutable state memory

`property.state_snapshot` is append-only at the database layer. UPDATE and
DELETE are rejected. Corrections are new versions linked by
`supersedes_snapshot_id`.

## Versioned Reality Gap policy

Classification thresholds live in
`intelligence.reality_gap_policy`, not hidden Java constants.

A measured gap answers **what differed**, not **why**.

## Reviewed error hypothesis

Non-UNKNOWN cause labels require evidence. A gap becomes a calibration
`CANDIDATE` only after a human-reviewed, non-UNKNOWN, evidence-backed
hypothesis. This is still not an automatic model update.

## Workspace isolation

Calibration projections are grouped and queried by workspace. Cross-workspace
learning from raw identifiable records is prohibited in this wave.

## Explicit non-goals

Wave 05 does not implement autonomous causal attribution, automatic model
retraining, cross-workspace pooling, Causal Atlas, Possible Worlds, FEM/CFD,
BIM reasoning or city simulation.

## Exit condition

OULA can reproduce from canonical records:

> What property state was known at decision time, what model/version produced the
> expectation, what the human chose, what actually happened, the measured gap,
> and whether a human-reviewed evidence-backed hypothesis considers that gap a
> valid calibration candidate.
