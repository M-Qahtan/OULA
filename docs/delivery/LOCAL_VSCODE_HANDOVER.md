# OULA — Founder Local VS Code Handover Plan (POST BUILD)
Founder instruction: this phase begins ONLY AFTER real repo build passes acceptance. No paid domain/cloud/production provisioning now.
## Development prerequisites
Java 21/Maven or Maven wrapper if added; Node supported by approved frontend; Docker Desktop with PostgreSQL16+PostGIS; VS Code Git/JDK/TS support. Validate actual versions at handover; keep `.env.example` without secrets.
## Intended local operating sequence
1. Clone `M-Qahtan/OULA`, checkout approved release tag; never use ROS code in this workspace.
2. Start local isolated PostGIS and dedicated test data. Run Flyway+backend via documented Java command. Validate `/actuator/health` under safe local exposure and OIDC local test identity flow.
3. Install `apps/web` pinned dependencies, configure `NEXT_PUBLIC_OULA_API_BASE_URL` to loopback dev API only, start Next.js, confirm CORS and scope; verify login before real requests.
4. Load repeatable Riyadh demo fixtures and run 3-minute canonical Golden Path. Reset fixtures between dry runs; fail closed if auth or backend down (show Demo Mode distinctly rather than pretending LIVE).
5. Perform founder sign-off checklist, then PLAN domain, TLS, privacy/regulatory compliance, hosting/location and cost, backup/recovery & support. Only after explicit approval move to paid cloud/field deployment.
## Approval requests before field real-data pilot
Consent-based real customer test users and rightful property data/photographs; applicable Saudi brokerage/listing compliance; official integrations with licensed providers; hosting/data processing terms and retention; budget ceiling and credentials owner. None assumed enabled by plan alone.
