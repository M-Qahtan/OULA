# OULA Wave 21 — Decision Learning & Evidence Evaluation Framework

## Objective
Operationalize a *scientifically conservative evaluation layer* over the existing
Wave 18 operational intervention history and Wave 19 rental advisory reviews.

This is an inspectable evidence-readiness framework, **not** an ML training
pipeline or proof that OULA caused an improvement.

## Sources and denominator discipline

- Wave 18 `interventions.review` / `outcome_observation`: human-reviewed
  intervention opportunities, observed follow-up states, policy comparability,
  and independently authorized Work Order evidence.
- Wave 19 `advisory.review_case` / `review_event`: captured rental recommendation,
  human disposition, VERIFIED documentary outcome observation and labels.
- Capture denominator is the number of **captured** cases. It is **not**
  the total eligible recommendations shown to users.
- Coverage measures captured cases with follow-up observations, **not**
  precision, recall, financial recovery, or causal recommendation success.
- A zero denominator yields null coverage instead of a misleading 0%.
- A property with no intervention records reports NO_RECORDED_INTERVENTIONS:
  it never concludes that property risk or the source's ground truth is zero.
- Current metrics aggregate all recorded history, without cohort/time-window
  normalization or a comparative control group. The response must flag this.

## Decision Evaluation Protocol v1
1. Capture observed case and intervention counts (separate datasets).
2. Calculate documentation coverage only when the denominator exists.
3. Count comparable before/after observations and VERIFIED_WORK_ORDER links,
   without treating them as a measure of causal effect.
4. Report improvement/inconclusive **human labels** separately.
5. Return explicit study prerequisites before anyone may claim effectiveness:
   eligible-denominator definition; pre-registered outcome and time horizon;
   baseline coverage; comparable controls/confounders; independent outcome
   verification; consent/fairness review; qualified causal/statistical review.
6. Set automaticTrainingAllowed=false unconditionally in v1.

## Architecture and security

- New Spring Modulith `evaluation` module depends only on authorized public
  `advisory` and `interventions` read services and IAM types.
- `InterventionService.evaluationHistory` adds a workspace-scoped history
  read that does **not** require a latest Vital snapshot.
- No migration: all evaluation data is derived from durable existing sources.
- The REST endpoint is GET-only:
  `/v1/properties/{propertyId}/decision-evaluation`.
- Requires PROPERTY_MANAGEMENT purpose and
  `oula.property.decision-evaluation.read` OAuth scope.
- Always Cache-Control: no-store. No side effects, audit writes, outbox events,
  policy changes, training or autonomous actions.

## Quality gates
- [ ] Descriptive metric computations and null-denominator behavior
- [ ] Unauthorized purpose/workspace/scope denied
- [ ] Property with no operational reviews does not error
- [ ] No accidental audit/outbox mutation on GET
- [ ] Spring Modulith boundaries, prior regression suite and CodeQL green
