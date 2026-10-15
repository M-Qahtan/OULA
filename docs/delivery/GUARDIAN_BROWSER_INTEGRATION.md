# OULA Guardian end-to-end acceptance

The new `/guardian-proof` is a **DEV_TEST_ONLY** view, not a production feature.

Canonical path: property-manager DEV JWT -> workspace + purpose + OAuth scope -> Java management enrollment -> obligation -> Guardian assessment -> read persisted PostgreSQL overview. It reuses existing OULA management services and does not replace the Property Domain. All development fixtures have DECLARED/DEV_FIXTURE provenance, and the workspace is independent of the buyer's workspace.

Security invariants: never activate `dev-auth` in production; never claim bank/government/escrow/signature integration; never turn an AI suggestion into spending authority. Calls do not fall back to mocked data. The page requires explicit build flags.

Required gates: Maven/PostGIS real DB CI, frontend typecheck/build, Playwright browser integration, negative access tests, review of bilingual responsive UI and secrets handling. It does not establish commercial readiness.
