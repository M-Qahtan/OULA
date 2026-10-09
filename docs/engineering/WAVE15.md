# OULA Engineering Wave 15 — Operational Decision Intelligence

## Mission
Convert immutable Property Vital Signs and evidence-aware trends into **explainable, human-reviewed intervention proposals** without delegating execution or assuming causality.

## Golden path
Authenticated human Property Manager → Workspace and OAuth purpose/scope authorization → Latest immutable Vital Snapshot → freshness gate → comparable Vital Trend → 8 explicit dimension rules → evidence-linked recommendation cards → human review.

## Components
- `advisory` Spring Modulith bounded context; allowed dependencies **only** on `iam` and `vitals`.
- `OperationalAdvisoryService`: pure deterministic rule-driven intervention reasoning.
- `OperationalAdvisoryController`: `GET /v1/properties/{propertyId}/operational-advice`, `Cache-Control: no-store`.
- `OperationalAdvisory`, `AdvisoryItem`, `EvidenceMetric` response contracts.
- OpenAPI 1.5.0 with independently scoped read access.

## Explainability
Each recommendation includes the exact dimension, source status, trend direction (if comparable), priority, action code, human gate, explanation, and observed source metrics. Source snapshot UUID, assessment policy key/version and advisory-rule version provide provenance. Never claim an estimated confidence score without validation.

## Built-in limitations
- Vital snapshots older than 48 hours (provisional design default) or significantly future-dated **only** cause a refresh recommendation, never stale intervention advice.
- `UNKNOWN` generates data-collection advice, not a false-green or failure.
- `POLICY_NOT_COMPARABLE` and source snapshot races suppress trend claims.
- `LIMITED_EVIDENCE` is explicit when the source overall assessment is UNKNOWN.
- GREEN dimensions do not generate corrective interventions.
- Priority ordering is deterministic and stable: HIGH → MEDIUM → DATA_QUALITY.
- Financial, legal, safety-critical and contractor actions always remain separate from advisory reads.

## Verification gates
1. Unit tests for RED status, source provenance, UNKNOWN, stale freshness, mismatched snapshots/policies, cross-purpose denial.
2. End-to-end PostgreSQL/PostGIS integration through real Vitals and MockMvc, including scope and workspace denials.
3. Spring Modulith architectural validation, Maven CI and CodeQL Java green.
4. Merge only the fully tested commit to `main`; never force push.

## Not delivered / research runway
No physical work, AI-generated diagnosis, external notifications, auto-approval, execution, provider outreach, causal effect estimation or autonomous learning. Subsequent work can persist human review decisions and measure evidence-backed before/after outcomes while retaining explicit uncertainty and experimental design controls.
