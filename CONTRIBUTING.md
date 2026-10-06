# Contributing to OULA

## Golden engineering path
1. Business need.
2. Bounded-context owner.
3. Domain model and invariants.
4. Authorization/compliance implications.
5. API and event contract.
6. Implementation.
7. Unit/domain/integration tests.
8. Audit and observability.
9. Outcome metric.
10. Documentation.

## Repository rules
- Never commit directly to \`main\` for feature work.
- Prefer short-lived branches and pull requests.
- No direct cross-module repository access.
- No LLM-to-database write path.
- \`Person != User\`, \`Property != Listing != Building\`.
- Facts must preserve provenance and truth status.
- Money is never represented with floating-point primitives.
- State changes in critical aggregates must be explicit and testable.
