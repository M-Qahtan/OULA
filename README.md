# OULA

**OULA — Built World Intelligence & Operating Layer**

OULA is being built as a human-first real-estate and built-world intelligence system. The Riyadh MVP focuses on a narrow, measurable operating loop while preserving an architecture that can evolve toward the long-term Built World Intelligence vision.

## North Star

Build a trusted operating and intelligence layer that helps people and organizations understand needs, evaluate properties and places, execute real-estate workflows, learn from outcomes, and progressively improve decisions across the built world.

## Current engineering direction

- Java 21
- Spring Boot + Spring Modulith
- Modular Monolith with explicit bounded contexts
- Event-driven core with transactional outbox
- PostgreSQL + PostGIS
- AI runtime isolated from the transactional source of truth
- Human-in-the-loop for sensitive or regulated actions
- Evidence, provenance, auditability, and explainability by design

## Riyadh MVP Golden Path

```text
Person
  -> Life / Intent
  -> Candidate Properties
  -> Property Passport / Truth
  -> LifeFit
  -> Decision
  -> Viewing / Offer
  -> Transaction
  -> Outcome
  -> Learning
```

## Repository rules

- No direct feature work on `main`.
- Use short-lived branches and pull requests.
- CI must pass before merge.
- Domain boundaries and source-of-truth rules are non-negotiable.
- `Person != User`
- `Property != Listing != Building`
- AI inference is never presented as a verified fact.

---

**Status:** Foundation initialization.
