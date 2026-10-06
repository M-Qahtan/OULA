# OULA — Built World Intelligence & Operating Layer

OULA is a Saudi-born, human-first real-estate and built-world operating and intelligence system. The product starts with the Riyadh real-estate life cycle and is engineered to evolve toward a trusted intelligence layer for properties, buildings, places and eventually the wider built world.

## North Star

Build a system that understands people and organizations, trusted property truth, place, market context, decisions, transactions and outcomes — then learns from reality to improve the next decision.

\`\`\`text
Person / Household
      ↓
LifeGraph + Intent
      ↓
Property + Place + Market + Truth
      ↓
Matching / LifeFit
      ↓
Decision
      ↓
Transaction
      ↓
Outcome
      ↓
Learning
\`\`\`

## Engineering architecture

- Java 21
- Spring Boot 4.1.1
- Spring Modulith 2.1.1
- Modular Monolith with explicit bounded contexts
- Event-driven core with Transactional Outbox / Consumer Inbox
- PostgreSQL + PostGIS as the operational source of truth
- AI runtime isolated from transactional authority
- Human/authority gates for sensitive or regulated actions
- Evidence, provenance, auditability and explainability by design

## Body of OULA

- **Brain:** \`intelligence\` — canonical reasoning/recommendation contracts and future simulation/model interfaces.
- **Heart:** \`platform\` + domain state machines — events, audit, idempotency and reliable change propagation.
- **Skeleton:** Spring Modulith bounded contexts and database ownership.
- **Nervous system:** REST/OpenAPI + AsyncAPI + domain events.
- **Immune system:** IAM, compliance, CI, CodeQL, dependency updates and explicit authority gates.
- **Muscles:** application/domain workflows such as Intent, LifeFit and Transaction lifecycle.
- **Memory:** facts, evidence, audit, outcomes and future Built World Memory.

See [OULA Organism Architecture](docs/architecture/OULA_ORGANISM.md).

## Canonical rules

- \`Person != User\`
- \`Property != Listing != Building\`
- Buyer, seller, tenant and broker are contextual roles, not person types.
- AI inference is never silently promoted to verified truth.
- Recommendation is not a binding decision.
- A domain owns its state; other modules interact through contracts/events.
- Money is not represented with floating-point primitives.
- Critical mutations are idempotent and auditable.
- Transaction state changes are explicit and tested.

## Current build wave

- Intent aggregate and invariants.
- Explainable deterministic LifeFit baseline.
- Explicit transaction state machine.
- Intelligence Kernel contract hooks.
- Flyway migrations through \`V015__organism_foundation.sql\`.
- PostgreSQL/PostGIS migration integration test.
- OpenAPI and AsyncAPI contracts.
- Architecture verification with Spring Modulith.
- CI, CodeQL, Dependabot and CODEOWNERS.

Next vertical slice:

\`\`\`text
Person -> Intent -> Candidate Retrieval -> LifeFit -> Shortlist -> Viewing -> Transaction
\`\`\`

## Local development

Prerequisites: Java 21, Maven, Docker.

\`\`\`bash
export DB_URL=jdbc:postgresql://localhost:5432/oula
export DB_USER=oula
export DB_PASSWORD=oula
mvn verify
\`\`\`

The integration test starts a PostGIS container through Testcontainers, so Docker must be available for the full verification suite.

**Status:** Riyadh MVP — living-core foundation.
