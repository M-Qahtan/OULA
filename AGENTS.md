# OULA — Canonical Build Instructions
## Mission
Build OULA as a cohesive human-centered Built World Intelligence Operating Layer. The repository M-Qahtan/OULA is the sole actual implementation. Lovable is a separate nonproduction expo simulator. ROS is independent.

## Ownership by paths
- `src/main/java/com/oula/**` and `src/main/resources/db/migration/**`: backend/domain owners
- `contracts/**`: architecture/integration contract owner; require backend + frontend review
- `apps/web/**`: frontend owner
- `docs/delivery/**`, `.github/agents/**`: program/architecture owner
- `.github/workflows/**`: QA and platform joint review.

## Mandatory handoff
Issue -> bounded branch -> focused commit -> CI -> independent review -> contract compatibility -> merge by maintainer. New experimental research stays isolated from 7-day Golden Path. Freeze backend truth and compatibility; build frontend to exact contracts. Always distinguish mock/demo from actual DB and authenticated behavior.

## Product boundaries
Person ≠ User; Property ≠ Listing ≠ Building. Facts carry provenance. LLM/inferred ≠ verified; recommendation ≠ human decision ≠ authorized action. Outcome observations never imply causal effect. Use local tests; no cloud/domain/paid spend until founder's later VS Code go-live work.

## Release
Read docs/delivery/EXPO_2026_RELEASE_TRAIN.md, FRONTEND_BACKEND_CONTRACT.md and RELEASE_GATES.md. Priority P0 blocks P1. No invented completion percent or passing tests.
