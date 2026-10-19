# OULA property-care integration — 19 October 2026

The HABITAT workspace now uses the same encrypted-session BFF as buying, property intake and transactions. Authorized users can enroll a property, read management state, add a declared obligation, assess near-term deadlines, and personally confirm a resolution. Every mutation is followed by a service read-back. Identical retries preserve their idempotency key. Account/workspace changes unmount the private panel and abort in-flight reads.

No new Java service or parallel database is introduced. The adapters consume the five existing `PropertyManagementController` endpoints. Management reads/writes, Guardian runs and action completion each require their contracted purpose and scope. All nested enrollment/obligation/signal/action resources must match the requested property and authenticated workspace. User-entered obligations carry `USER_DECLARED`; recording a resolution is not a certified inspection or payment instruction.

The OpenAPI file had a malformed currency pattern, an accidentally duplicated schema suffix, and an unquoted colon in a description. The document is now parsed as a whole with unique keys and all internal references resolved. All 96 paths are preserved. Six response schemas now describe the existing Java records; the browser/server contract matrix includes the five care operations.

Local verification: TypeScript, the production Next.js build and 131 unit tests pass. All 52 browser/OIDC tests pass against the explicitly synthetic local contract harness; they are not Java/PostgreSQL evidence. The new browser integration test creates a separate fictional property, enrolls it, saves an obligation, replays the identical command without duplication, evaluates it, records resolution, reloads the page and reads the persisted state. Java's canonical states remain `SATISFIED` for the obligation, `RESOLVED` for the signal and `COMPLETED` for the action.

The actual Java/PostgreSQL care test initially exposed a missing action-completion scope in the fixed development manager fixture. This patch adds that scope only to the fictional `dev-auth` fixture; production IAM policy is unchanged. Fresh CI results are recorded on [PR #122](https://github.com/M-Qahtan/OULA/pull/122). Local synthetic tests do not establish production identity readiness.

Nullable legacy signal/action fields follow migration V011 and the existing Java records. Response validation still rejects every foreign property/workspace nested resource. The complete OpenAPI document and all internal references are checked in unit tests.

Production identity/provider onboarding, live financial/government integrations and scientific engineering/city engines remain outside this delivery. Maintainer review and merge gates remain required.
