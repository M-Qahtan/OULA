# Connected workspace validation

Verdict: **local integrated candidate**. The complete future cinematic vision and commercial production readiness are not certified.

| Gate | Result |
|---|---|
| Canonical Java / real PostgreSQL + PostGIS | 159 tests passed, zero skipped; 30 Flyway migrations |
| Frontend unit tests | 90 passed |
| Typecheck / clean typecheck / production compilation | Passed |
| Browser → signed Java identity → PostgreSQL | 14 integration tests passed |
| Final affected buyer and owner journeys | 2 additional tests passed after the UI state-isolation change |
| UI + synthetic OIDC/API browser suite | 35 passed |
| Automated accessibility checks | AR/EN at 1920, 1024 and 390 px; no reported violations in checked workspace states |
| Strict design/UX static audit | Zero errors/warnings |
| Production npm dependency audit | Zero reported advisories at validation time |
| Startup and isolated verification scripts | Shell syntax passed; Docker execution not available in this runtime |

The Java/PostGIS/Next stack was executed natively. The test-only runtime adapter used to launch PostgreSQL in the single-UID execution environment is not included in the product. Docker startup must still be exercised on the founder's supported workstation.

The browser suites distinguish the synthetic OIDC contract harness from the actual Java/PostgreSQL path. Existing office/Guardian proof fixtures assume a fresh test database; the full integration suite was run on a newly initialized isolated database. A browser retry assertion now awaits the actual command response before inspecting its idempotency receipt, and gateway regression assertions follow the relocated `/journey` route.

The machine-readable report includes source digests and material limitations. No public deployment, paid resources, external integration, production identity setup or automatic model training was performed. Maintainer review and fresh remote CI on the PR remain required before merge.
