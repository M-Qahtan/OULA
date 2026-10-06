# OULA System Body v1

OULA is implemented as one modular organism, not a collection of unrelated features.

| Organ | Module | Responsibility |
|---|---|---|
| Identity / immune boundary | iam + compliance | actor, workspace, purpose, authority |
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
| Property lifecycle | operations | Guardian signals and actions |
| External boundary | integration | provider-neutral external references |
| Circulation | platform.outbox | reliable domain-event propagation |

Non-negotiable:
1. Person != User.
2. Property != Listing != Building.
3. AI inference != verified fact.
4. Recommendation != Decision.
5. Prediction != Outcome.
6. Orchestration coordinates but never owns canonical domain state.
7. ROS remains an independent future integration, not an OULA module.
8. Riyadh MVP remains focused while contracts stay extensible toward Built World Intelligence.
