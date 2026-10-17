# OULA — Independent QA Continuation / Release Evidence
**Date:** 2026-10-17 (Asia/Riyadh)  
**Program:** #38 · **Acceptance gate:** #42  
**Candidate baseline:** `main@b0ec6a193ee6d85507696b980a266c024f3c6e33` (merged PR #109)  
**Decision:** **NO-GO for field/production acceptance** until the outstanding gates below are evidenced; automated CI passing is not independently witnessed acceptance.

## 1. Verified evidence on the exact merged baseline

All four *push* workflows completed successfully on the same SHA (not extrapolated from an earlier PR HEAD):

| Gate | Run | GitHub conclusion |
|---|---|---|
| Java 21 Maven verify + PostGIS 16/3.4 | [CI #39041448499](https://github.com/M-Qahtan/OULA/actions/runs/39041448499) | SUCCESS |
| Next.js typecheck, unit, production build, browser smoke | [Frontend #39041448547](https://github.com/M-Qahtan/OULA/actions/runs/39041448547) | SUCCESS |
| Next.js browser → signed dev JWT → Spring API → real PostgreSQL | [Browser #39041448494](https://github.com/M-Qahtan/OULA/actions/runs/39041448494) | SUCCESS |
| Java static analysis | [CodeQL #39041448519](https://github.com/M-Qahtan/OULA/actions/runs/39041448519) | SUCCESS |

**Interpretation:** this establishes the above automation gates on the precise main commit, not a penetration test, independent reviewer approval, load test, production identity onboarding or official regulatory/financial integration. No release, deployment, licence or outside-organization integration is implied.

## 2. Scoped QA change proposed after the baseline

`apps/web/e2e-integration/office-security-boundary.integration.ts` adds negative HTTP/API regressions to the existing integration runner, using signed fictional fixtures and actual Java/PostGIS:

- Missing JWT → 401; invited but unscoped actor → 403 on roster.
- Wrong purpose, foreign workspace header, and foreign actor/workspace mismatch → 403.
- An authorized administrator can retrieve a workspace-contained, `no-store` roster.
- The invitation administrative list is forbidden to the invitee.
- The invitation recipient's own inbox is scoped to its signed actor+workspace and is `no-store`.
- Administrator without recipient-acceptance scope cannot read a recipient inbox.
- POST without `Idempotency-Key` → 400; invitee cannot self-promote to invite-authority → 403.
- Tests do **not** change fixture state; safe after the one-shot invite/accept/revoke scenario.

**Review gate:** rerun CI, Frontend CI, Browser Integration and CodeQL on the **new PR HEAD**; inspect failed-job logs before any approval. A green run is necessary but does not replace an independent security reviewer.

## 3. Remaining #42 acceptance gaps (do not mark complete)

| Required gate | Current evidence | Decision |
|---|---|---|
| Signed local browser / API / DB Golden Path | GitHub Browser Integration success on exact baseline | Automated PASS, must rehearse |
| Workspace/purpose/role security | Java regressions + browser test; expanded denial coverage in pending QA PR | Pending PR verification + independent review |
| Arabic RTL/English LTR, 390/1024/1920 | Automated viewport and route smoke tests | Manual visual, contrast, focus and screen reader audit pending |
| 3-minute timed guided journey | Not witnessed or timed | Pending |
| Visitor-to-visitor reset and offline fallback | Local reset commands documented; office membership fixture is one-shot | Operator rehearsal pending |
| No false `VERIFIED`/government/bank/licensing claims | `DEV_TEST_ONLY`/fictional labels in reviewed proof routes | Needs complete walkthrough / content signoff |
| Product/site readiness | No external IdP or approved production organization onboarding | Explicitly **not production-ready** |

## 4. Stop/ship rule

1. Do not merge this PR if any relevant head-SHA gate fails or the diff leaks authentication data.
2. Obtain reviewer-authored approval independent of the change author; do not manufacture or self-assert independent approval.
3. Record the **exact merge SHA** and rerun/inspect main push CI on it.
4. Before 19 October candidate review: execute the 3-minute walkthrough against the target local machine, check a second-visitor restart/reset, and classify P0/P1/P2 defects under issue #42.
5. Before any actual audience or production exposure: verify the founder's acceptance, licensing/authority, privacy and operational safeguards. No paid cloud, domain, government, payment or registry integration is authorized by this record.

**Next status:** automated main-SHA checks verified; additional PR regressions await their own CI; exhibition acceptance remains NO-GO pending witnessed evidence.
