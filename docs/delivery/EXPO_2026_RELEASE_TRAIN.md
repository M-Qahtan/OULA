# OULA — EXPO 2026 CANONICAL SOURCE RELEASE TRAIN
**Date:** 2026-10-10. **Timezone:** Asia/Riyadh. **Parent tracking:** https://github.com/M-Qahtan/OULA/issues/38
**Decision:** Build real Frontend+Backend together inside GitHub M-Qahtan/OULA; Lovable only demo/virtual showroom. Local production activation, domain, paid cloud and regulatory integrations happen later with founder in VS Code. **No deployment promised in 7 days.**
## Sprint target and product scope
Deliver *reviewable, runnable local, source-based integrated vertical slice* by Monday 19 October, then founder acceptance 20–22, field rehearsal 23, frozen show demo 24. Conference expected 25–26 Oct subject to founder attendance confirmation. Product global vision preserved, rollout is Saudi/Riyadh-first MVP. No claim of full global system by 19 Oct.
**Hard priority:** backend fact/auth truth; genuine web UI; one end-to-end person -> intent -> match -> property passport -> human decision -> viewing/offer -> deal room -> recorded outcome, with broker-lite operations and Guardian read-only. Continuous AI/engineering simulation beyond a scientific prototype = honest roadmap, not fake functionality.
## Parallel teams and dependencies
| Stream / agent | Issue | Outputs | Blocking dependencies |
|---|---|---|---|
| Architecture & contracts / oula-chief-architect | #38, #52 | canonical contract, consistent domain states | none |
| IAM & platform / oula-platform | #46 | test identities, purpose/workspace/scopes, local env | none |
| Properties / oula-backend | #47 | real property passport/listing endpoints + fixture | IAM #46 |
| Golden Path API / oula-backend | #39 | coherent create/read flow & seed | #46, #47, #48, #49 |
| Matching & LifeFit / oula-backend | #48 | persisted typed match and explanation | #47, #46 |
| Transaction OS / oula-backend | #49 | viewing/offer/deal state machine | #46, #48 |
| Design system / oula-frontend | #51 | design tokens, responsive AR/EN shell | none |
| Real frontend / oula-frontend | #40 | apps/web full visitor journey | #51, #52; can scaffold day1 |
| Integration / oula-integration | #41, #52 | typed client, auth, E2E integration | #39, #40, #46 |
| Guardian / property operations / oula-backend | #56 | read-only tenancy/Guardian and vitals | #47, #46 |\n| Reality / intelligence / oula-product-science | #50 | provenance and scientific accuracy | #47, #48 |
| QA and security / oula-qa-security | #42 | matrix, regression, GO/NO-GO | ongoing all streams |
| Local VS Code / oula-platform | #53 | repeatable local setup | #39, #40, #41 |
| Demo & field / oula-product-science | #44 | user tests, truthful script | #41, #42 |
| Infra future approvals / oula-platform | #43 | costed plan only; no cloud now | founder post-acceptance |
**Note:** Agent profile files do not run themselves. GitHub Copilot cloud assignment was attempted on issue #39 and GitHub API returned 403. Agent activation must be enabled/confirmed; unassigned issues remain status PLANNED.
## Seven Saudi business days, 11–19 Oct (Sun-Thu + Sun-Mon)
| Day | Owner priorities | Concrete exit gate |
|---|---|---|
| D1 Sunday 11 | architect freezes contract; front-end scaffold and brand tokens; IAM test harness | branch + PR + runnable hello and schema matrix |
| D2 Monday 12 | profile/intent/property read-write; identity protections; frontend journey shell | repeatable Riyadh fixture and typechecked views |
| D3 Tuesday 13 | persisted match/LifeFit; frontend matching/passport | deterministic scores from true backend API |
| D4 Wednesday 14 | viewing/offer transaction, idempotency; frontend Deal Room | human-governed lifecycle DB integration |
| D5 Thursday 15 | Guardian read model, broker-lite; real front/back adapter | authenticated full Golden Path on staging/local |
| D6 Sunday 18 | independent QA, Arabic/English, performance/security, fixes | zero open P0, reproducible CI and owner handoff |
| D7 Monday 19 | end-to-end demo candidate, local runbook & founder artifact | candidate tag/build with evidence; NO unapproved public deployment |
| 20–22 | founder tests, recorded defects, fix, re-run CI | signed acceptance or honest NO-GO |
| 23 | show rehearsal + offline fallback | 3-min and 10-min verified scripts |
| 24 | code/design frozen, fallback ready | final GO/NO-GO |
## Hourly engineering control (not claim of autonomous operation)
08:00 blockers & PR statuses; 09:00 2-team contract sync; 10:00–12:00 isolated implementation; 13:00 unit/DB CI; 14:00 frontend/backend connection; 15:00 performance & auth; 16:00 P0 defect triage; 17:00 3-minute Golden Path; 18:00 handoffs/QA; 19:00 executive report. Hourly automated watcher observes blockers only, not commits code or assigns bot automatically.
## Stop/reassign
A role is released after tested, reviewed, merged handoff + no P0. Next highest-dependency issue only after predecessor passes. Blocked >2 work hours: escalate to founder with one precise action and continue independent tasks. Any PR breaking API or tests is not accepted. No secret/paid infrastructure procurement or public publishing without express owner approval.
