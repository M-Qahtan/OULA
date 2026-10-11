# OULA — Universal Experience & Ecosystem Architecture v1.0

**Status:** Proposed Canonical / independent review required  
**Date:** 2026-10-11 · Riyadh-first, global core  
**Founder:** Mohammed Qahtan  
**Repository:** M-Qahtan/OULA  
**Implementation dependency:** EXPO P0 #46/#47/#48/#49. This document does not assert merged #47, production integrations or full world coverage.

## 1. Executive architecture decision

**One OULA · One Intelligence · Contextual Experiences.**

OULA is a coherent operating organism. Personal and organizational users have one identity, multiple isolated workspaces, contextual roles, delegated capabilities and adaptive experience lenses. **A lens is presentation, not authorization or a new source of truth.**

A user's effective journey is determined by authenticated actor, workspace membership, declared purpose, permitted scopes, resource relation, consent/delegation, policy, actual system capability and preference. The backend independently checks every read and command.

## 2. Invariants

- Person != User. Property != Listing != Building. Owner/tenant/buyer/broker are contextual relationships.
- Domain owns truth; cross-domain writes happen via approved API/command/event, not direct table mutation.
- VERIFIED/DECLARED/OBSERVED/CALCULATED/ESTIMATED/AI_INFERRED/DISPUTED/EXPIRED are never collapsed into one badge.
- Recommendation != human decision != authorized execution; simulation != actual outcome; signed lease != occupancy.
- Workspace + Purpose + Scope + Resource Relation + Consent/Delegation + Policy; **fail closed** on uncertainty.
- ROS is independent and only a future contract-mediated integration, never a module within OULA.
- AI outage cannot turn a forbidden operation into an allowed operation; no fake live government/bank integration.

## 3. Experience lenses and stakeholders

| Experience lens | Users | Outcome |
|---|---|---|
| PULSE | Consumer, buyer, tenant, household | Intent -> matching -> trusted shortlist -> human decision |
| HABITAT | Owner, landlord, tenant, property/facility manager | Contracts, occupancy, obligations, maintenance, guardian |
| NEXUS | Broker, office, bank, insurer, legal/valuation partner | Leads, transactions, delegated workflows and partner cases |
| PRISM | Investor, fund, enterprise portfolio | Cash flow, risk, scenarios and development decisions |
| FORGE | Developer, architect, structural/civil/MEP, contractor | Projects, simulations, BIM and licensed professional review |
| CIVIC | Government, planning authority, city/transport planners | Aggregated built-environment insights and lawful operations |

These are **not new domain modules**: each reads or commands only the established backend bounded context. A user can switch workspaces and professional contexts with no account duplication. Never mix corporate assets and a personal workspace without verified authorization.

## 4. Component architecture

```text
Authenticated actors / organizations
       -> Adaptive Experience (AR/RTL + EN/LTR web/mobile)
       -> Workspace + Purpose + Policy Gate
       -> Personal Agent / Workflow Coordinator (no truth ownership)
       -> People | Intent | Spatial | Property | Market | Matching
          Decision | Transaction | Tenancy | Operations | Services
          Settlement refs | Integration trust | Intelligence Kernel
       -> Domain facts/evidence + immutable audits + outbox
       -> Global Integration Fabric + country-specific regulatory pack
```

The existing code remains Java 21/Spring Boot/Spring Modulith, PostgreSQL/PostGIS/Flyway and Next.js/TypeScript. Python AI services are isolated adapters when evaluated and needed. Domain modules are extracted into microservices only if operational/organizational evidence justifies the distributed complexity.

## 5. Organ register and status

| Organ | Existing authoritative module | Status/gate |
|---|---|---|
| Identity + immune system | iam, compliance | Implemented foundation; fully test resource policies |
| Human intention | people, intent | Implemented foundation; golden-path evidence |
| Built-space and property | spatial, property, documents, market | Implemented foundation; #47 intake still draft (#72) |
| Decision senses | matching | Deterministic/structured; verify persistent matching + UX |
| Brain and memory | intelligence, decision | Research-capable foundation; no false causal claims |
| Heart and circulation | orchestration, platform.outbox | Existing commands/events/audit; prove idempotency |
| Muscles and lifetime | transaction, operations, tenancy, services | Existing kernels; human approval; distinct lease/occupancy/receipts |
| External arms | integration, settlement | Trust/reference boundaries only; no actual fund movement |
| Adaptive skin | apps/web + scoped manifest | This new proposed organ: read-only scope projection |
| Engineering/city expert society | Future FORGE/CIVIC engine | Research/future until physical and professional validation |

**Implemented foundation ≠ production live integration.** Verify using CI, CodeQL, PostgreSQL/PostGIS, contract and authenticated browser E2E on the same commit.

## 6. Adaptive Experience Manifest contract

`GET /v1/experience/manifest` requires bearer JWT + X-OULA-Workspace-ID + X-OULA-Purpose and `oula.experience.read`. The existing WorkspacePurposeAuthorizer validates the actor, token workspace memberships, purposes and scope. The returned list is an **intersection** of purpose-specific known capabilities and scopes actually present in that JWT.

- PROPERTY_DECISION_SUPPORT -> PULSE; intent/match/property-passport scopes.
- PROPERTY_MANAGEMENT -> HABITAT; passport/management overview scopes.
- TRANSACTION_EXECUTION -> NEXUS; transaction read/advance scopes.
- Autonomous/integration purposes are deliberately not exposed by this UI discovery endpoint.
- The response is `Cache-Control: no-store`; the client rejects a foreign-workspace or wrong-purpose response.
- A capability is **navigation metadata**, never authority. Each target endpoint enforces its own policy and resource ownership.

Future: server-side membership/role and delegated relationship projection with auditable policy decision. Do not infer legal licensing or OIDC access solely from a role label.

## 7. Agent safety and workflow governance

User goal -> scoped planner -> vetted tools -> policy/evidence/consent gate -> professional/human approval where required -> domain command -> outbox/audit -> observed outcome. Agent prompts cannot confer signature, fund-transfer, government filing, property-registration or engineering approvals.

## 8. Golden Path / blockers / staged implementation

1. **P0 truth first:** finish independent review, branch reconciliation, duplicate-create 409, cross-workspace listing reads and PostGIS demo seed for #47; pass CI and CodeQL.
2. **Experience foundation:** manifest endpoint, typed TypeScript transport, deny tests, test without a real identity provider; no visual claim of live login.
3. **Actual integration:** OIDC session/BFF, browser requests to authenticated backend, persisted intent -> match -> passport -> viewing/offer -> transaction -> audit/outcome.
4. **Property lifetime:** read-only Guardian + evidence-backed management, tenancy financial distinctions, role-based owner/tenant/broker views.
5. **Institutional packs:** organization delegation, finance/insurance/gov integrations only after provider agreements, professional authorizations and testing.
6. **Engineering and cities:** checked numerical solvers, physics simulation, BIM/GIS, professional approval and scientific evaluation; future horizon.

## 9. Acceptance, risk and rollback

- Java 21 `mvn -B -ntp verify` with real PostgreSQL/PostGIS, Spring Modulith boundary tests, 401/403 cross-workspace/purpose/scope regressions, CodeQL.
- Frontend typecheck, Vitest, build and Playwright AR/EN RTL/LTR + mobile; separate DEMO/SANDBOX/LIVE labeling.
- Contract drift matrix and OpenAPI updated before wiring browser flows; no implicit fake-data fallback.
- Independent security review for personal/corporate boundaries; scoped branch and PR only, no direct main.
- No paid cloud, public deployment, bank/gov connection, change to ROS or production activation without separate founder decision.

**North Star:** successful real-estate outcomes with traceable evidence, not listing counts or AI claims.
