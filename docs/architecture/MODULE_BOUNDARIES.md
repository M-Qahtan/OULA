# Module Boundaries

| Module | Owns | Must not own |
|---|---|---|
| iam | workspace, identity, membership, consent | person life profile |
| people | person, household, life context | login credentials |
| spatial | place and spatial representation | property commercial identity |
| property | asset identity, facts, passport inputs | listings or transaction state |
| intent | structured user/entity objectives and constraints | search results |
| market | listing lifecycle and market observations | property truth |
| matching | candidate scoring and LifeFit results | property/intent state |
| decision | scenarios, trade-offs, outcomes | binding authority |
| transaction | deal lifecycle, offers, approvals | property truth |
| documents | document versions and evidence | domain business state |
| compliance | policy decisions and jurisdictional gates | business aggregate state |
| operations | post-transaction asset operations | property canonical identity |
| integration | external references/synchronization | canonical domain meaning |
| intelligence | observations, assumptions, model/recommendation contracts | source-of-truth domain state |
| platform | audit, idempotency, event transport primitives | business rules |

Cross-module interaction uses application contracts and domain events. A module never reaches into another module's repository to mutate its state.
