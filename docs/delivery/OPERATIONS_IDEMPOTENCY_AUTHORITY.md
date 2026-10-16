# Actor-scoped OULA Operations and Human Oversight Receipts

**Implementation:** 12 operations modified in the existing Java controller layer.

## Coverage
- 6 PropertyOperations work-order commands: create, approve, assign, start,
  completion submission and verification.
- 3 HumanReview commands: capture, record a decision, observe an outcome.
- 2 Intervention review/outcome commands.
- 1 PropertyVitals assessment command.

## Model
The already authenticated `AccessContext` supplies verified workspace, actor and
purpose to the existing versioned `RequestFingerprint.scoped` function. Neither
the visual user role nor caller-supplied headers alone grant permission. Existing
operation scope and domain approval requirements stay intact. The complete
idempotency key still has workspace-level uniqueness; a different actor colliding
on the same key receives 409, never the first actor's stored response.

Operations supporting both `PROPERTY_MANAGEMENT` and `AUTONOMOUS_EXECUTION`
are purpose-separated. This must not be misconstrued as approval for autonomous
financial execution; policy and human confirmation gates remain in place.

## Verification
Negative regression tests for work-order creation and intervention review
assert other authorized workspace members cannot replay the first actor's result.
Tests for each unchanged lifecycle plus exact-head Maven/PostGIS verification,
CodeQL, and browser integration must all pass before merge.

No outside service, real maintenance dispatch, legal approval or payment is implied.
