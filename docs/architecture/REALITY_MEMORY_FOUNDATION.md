# OULA Wave 04 — Reality Memory & Property Intelligence Foundation

Status: **Architecture contract / implementation gate**

## Mission

Turn OULA's first executable Decision Intelligence loop into durable, evidence-backed learning about a property and the decisions made around it.

Wave 04 extends the canonical loop without creating a second intelligence architecture:

```
Property State
  -> Evidence
  -> Recommendation / Expected State
  -> Human Decision
  -> Observation / Actual State
  -> Outcome
  -> Reality Gap
  -> Calibration
  -> Learning
```

## Architectural rule

Wave 04 MUST extend the existing Spring Modulith body. It MUST NOT create parallel sources of truth, duplicate evidence ledgers, or premature microservices.

Canonical ownership remains:

| Concern | Canonical owner |
|---|---|
| Property identity and verified property facts | `property` |
| Spatial truth | `spatial` |
| Listing / market observation | `market` |
| Evidence ledger for intelligence claims | `intelligence.evidence` |
| Recommendation / assumption / model / observation / outcome | `intelligence` |
| Human decision-case lifecycle | `decision` |
| Transaction state | `transaction` |
| Post-transaction property lifecycle | `operations` |
| Purpose / authority | `iam + compliance` |
| Audit / idempotency / outbox | `platform` |
| Cross-domain journey coordination | `orchestration` |

## Slice 04.1 — Property temporal state

Introduce a versioned property-state representation owned by `property`.

A snapshot is a time-bound representation of known state, not a replacement for canonical property facts.

Minimum semantics:

- propertyId
- effectiveAt
- recordedAt
- physicalState
- marketState
- occupancyState
- operationalState
- evidenceRefs
- source/provenance
- version

### Invariants

1. Snapshot != verified fact.
2. A snapshot cannot silently upgrade inferred state to verified truth.
3. Historical snapshots are immutable.
4. Corrections create a new version with lineage.
5. Every material state value exposes provenance or explicit unknown status.

## Slice 04.2 — Reality Case

Add a read/composition model in `intelligence` that assembles an existing decision-learning chain:

```
subject
 + context
 + evidence
 + assumptions
 + modelVersion
 + recommendation
 + humanDecision
 + observations
 + outcome
 + variance
 = RealityCase
```

RealityCase MUST reference canonical records. It MUST NOT copy and become authoritative for property, transaction or decision state.

## Slice 04.3 — Reality Gap v1

Reality Gap v1 is deterministic measurement, not causal inference.

For comparable expected and actual metrics:

```
absoluteGap = actual - expected
relativeGap = (actual - expected) / abs(expected)
```

Zero-denominator behavior MUST be explicit.

Initial classification:

- WITHIN_EXPECTATION
- MINOR_VARIANCE
- MATERIAL_VARIANCE
- SEVERE_VARIANCE
- UNKNOWN

Thresholds MUST be versioned policy/configuration, not hidden constants.

### Scientific invariant

A measured gap says **what differed**. It does not establish **why it differed**.

No causal label may be emitted merely from correlation or model self-explanation.

## Slice 04.4 — Error taxonomy

Provide a controlled, extensible taxonomy for reviewed hypotheses:

- DATA_QUALITY
- MISSING_VARIABLE
- MODEL
- BEHAVIORAL_CHANGE
- MARKET_SHIFT
- REGULATORY_CHANGE
- SPATIAL_CHANGE
- EXECUTION
- MEASUREMENT
- UNKNOWN

For Wave 04, attribution is human-reviewed and evidence-backed. Automated causal attribution is explicitly out of scope.

## Slice 04.5 — Model accountability

Every material recommendation must remain traceable to:

- modelId
- modelVersion
- input lineage / source snapshot references
- evidence set
- assumption set
- generatedAt
- confidence
- uncertainty
- risk class

Calibration projections may compute:

- prediction calibration
- outcome coverage
- material variance rate
- decision override rate
- evidence coverage
- unknown outcome rate
- time-to-outcome
- recommendation acceptance

These metrics are observational. They MUST NOT silently rewrite operational truth or model versions.

## API and event contract

All state-changing endpoints remain:

- authenticated
- workspace/purpose bound
- attributable to an actor
- idempotent where retry is plausible
- audited
- backed by transactional outbox events

Candidate event names:

- `property.snapshot.recorded.v1`
- `intelligence.reality-gap.measured.v1`
- `intelligence.error-hypothesis.reviewed.v1`
- `intelligence.calibration.updated.v1`

Final event payloads must carry identifiers and lineage, not duplicate entire aggregates.

## Database evolution

Wave 03 owns V008.

Wave 04 starts at **V009** after rebasing on canonical `main`.

Rules:

1. Never edit V001–V008.
2. Never create a second evidence ledger.
3. Prefer normalized authoritative relations over JSON-only lineage.
4. JSON may be retained only as a non-authoritative compatibility/read snapshot.
5. Foreign-key direction must respect module ownership.

## Security and privacy

Reality Memory is purpose-bound institutional memory, not unrestricted behavioral surveillance.

Required:

- purpose limitation
- workspace isolation
- minimum necessary data
- explicit provenance
- retention policy hooks
- subject/data-rights hooks where applicable
- no sensitive-person inference as a hidden matching feature
- no cross-workspace learning from raw identifiable records

## Test gates

Wave 04 cannot merge without:

1. Flyway V001 -> V009+ clean migration on PostgreSQL 16/PostGIS.
2. Spring Modulith boundary verification.
3. Property snapshot immutability/version-lineage tests.
4. Verified-vs-inferred truth negative tests.
5. Reality Gap deterministic calculation tests.
6. Zero/unknown/missing expected value tests.
7. Evidence-required material attribution tests.
8. Human-reviewed error-hypothesis tests.
9. Purpose/workspace authorization negative tests.
10. Idempotent replay tests.
11. Audit + correlation + outbox lineage tests.
12. Existing Wave 02/03 regression suite green.
13. CodeQL green.

## Explicit non-goals

Wave 04 MUST NOT implement:

- autonomous causal attribution
- Causal Atlas
- Possible Worlds simulator
- Built World Foundation Model
- autonomous transaction agents
- FEM/CFD engineering solvers
- a generic ERP
- a second Property/Truth/Evidence system

Those capabilities must later consume this foundation.

## Exit condition

Wave 04 is complete when OULA can answer, with evidence and lineage:

> What did we know about this property at decision time, what did the system expect, what did the human decide, what actually happened, how large was the gap, and which model/version produced the expectation?

That answer must be reproducible from canonical records without reconstructing truth from logs or opaque model output.
