# OULA — Independent Release and Acceptance Gates
## Scope levels
1. **Architecture conceptual:** documentation only.
2. **Backend implemented:** code exists and unit tests pass.
3. **Integrated:** real DB/PostGIS, real API and frontend request after auth, negative tests.
4. **Field pilot candidate:** local reproducible build + acceptance by founder.
5. **Production-ready:** licensed/legal controls, live provider agreements, monitoring backup/security/performance, privacy consent, post-release support. NOT promised by Oct 19.
## Required acceptance checks
- Backend `mvn -B -ntp verify` Java21 + PostgreSQL/PostGIS and all Flyway migrations, Spring Modulith boundary test. CI and CodeQL green on PR HEAD after last edit.
- Frontend `npm run typecheck`, `npm run test`, `npm run build` plus browser E2E at 1920x1080, 1024x768, 390x844 and Arabic RTL/English LTR, focus/keyboard/contrast/reduced-motion.
- Contract drift gate: every network route used by actual frontend is documented by OpenAPI, errors and OIDC workspace/purpose verified. Deny cross-workspace and unauthorized roles; protect against replay/double submit and optimistic concurrency.
- Golden path end to end with actual DB writes and read backs, readable property source, human-approved decision/offer and transaction status; capture audited outcome. Preview screens must clearly label DEMO, SANDBOX or FUTURE.
- Seed includes fictional Riyadh dataset explicitly labeled DEMO, legally reusable imagery/data; no invented 'VERIFIED' evidence.
- Expo QA: 3-minute visitor scenario, operator-only reset, local offline fallback, image/content rights, no dead core CTA, landing ready from restart.
## P0 release blockers
Leakage of personal records; wrong workspace or purpose authorization; silent fake API; mismatch between backend and UI deal states; false bank/government verified assertion; backend migrations fail; frontend build fails; primary journey dead end; unrecoverable demo reset.
## Review format
Each issue/PR: changelog, source boundaries touched, test command/result (timestamp, branch SHA), screenshots if UI, data claims, known limitations, rollback. Founder gets GO/NO-GO report by 22 Oct. No uncontrolled merge, public deploy or paid resource activation.
