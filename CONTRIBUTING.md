# Contributing to OULA

## Engineering Golden Path
Every material feature should answer, in order:

1. Which business/user outcome does this serve?
2. Which bounded context owns the meaning and state?
3. What invariants must hold?
4. What authorization/compliance rules apply?
5. What API/event contracts change?
6. What tests prove the behavior and failure modes?
7. What audit/observability is required?
8. Which product or outcome metric will measure value?
9. Which architecture/decision record must be updated?

## Repository rules
- Do not develop features directly on `main`.
- Use short-lived branches and pull requests.
- CI and security checks must pass before merge.
- No direct cross-module table writes.
- No LLM-to-database authority path.
- `Person != User`.
- `Property != Listing != Building`.
- `AI Inference != Verified Fact`.
- `Recommendation != Decision`.
- `Simulation != Reality`.
- Preserve evidence, provenance and state history.
- Do not hardcode Saudi-only semantics into the global core when a jurisdiction adapter is the correct boundary.
