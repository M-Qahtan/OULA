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
