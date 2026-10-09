# OULA System Body v1

OULA is implemented as one modular organism, not a collection of unrelated features.

| Organ | Module | Responsibility |
|---|---|---|
| Identity boundary | iam | actor, workspace, purpose and OAuth authority |
| Immune / trust system | compliance | deterministic policy, financial limits, evidence/verification gates and human approvals |
| Human organ | people | person, household, LifeGraph |
| Spatial organ | spatial | place and built-world coordinates |
| Property organ | property | asset identity and truth-aware facts |
| Market organ | market | listings and market observations |
| Intent organ | intent | structured intent |
| Decision senses | matching | candidate retrieval and explainable LifeFit |
| Intelligence brain | intelligence | recommendation, decision, outcome |
| Executive nervous system | orchestration | coordinates journeys; owns no domain truth |
| Transaction muscles | transaction | explicit deal state machine |
| Evidence memory | documents | evidence references |
| Property lifecycle | operations | Guardian, management and human-authorized Work Orders |
| Service network | services | provider profiles, capabilities, quotes and observed provider outcomes |
| Settlement boundary | settlement | external settlement references; never fund movement |
| External boundary | integration | provider-neutral external references |
| Autonomic / vital system | vitals | deterministic property pulse, coverage and operational-health observations |
| Advisory reasoning organ | advisory | evidence-linked human-review cards derived from immutable Vital observations; no external execution |
| Human feedback memory | interventions | append-only human review and separately observed outcomes without causal claims |
| Tenancy / human-place relationship | tenancy | units, rental agreements, contractual dues and evidence-recorded occupation |
| Circulation | platform.outbox | reliable domain-event propagation |

Non-negotiable:
1. Person != User.
2. Property != Listing != Building.
3. AI inference != verified fact.
4. Recommendation != Decision.
5. Prediction != Outcome.
6. Policy evaluation is deterministic and auditable.
7. Autonomous execution never inherits human authority implicitly.
8. Orchestration coordinates but never owns canonical domain state.
9. ROS remains an independent future integration, not an OULA module.
10. Riyadh MVP remains focused while contracts stay extensible toward Built World Intelligence.
11. Vital observations are derived, versioned and immutable; they do not rewrite canonical truth.
12. Signed lease ≠ occupancy ≠ government registration; rent due ≠ rent paid.
13. Advisory ≠ approval or action; data gaps and stale snapshots prohibit unsupported operational conclusions.
14. Advisory review ≠ spending authority; later improvement ≠ scientifically established causality.
