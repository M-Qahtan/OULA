# OULA Security Policy

OULA treats identity, workspace boundaries, purpose, consent, property evidence, transactions, and intelligence recommendations as security-sensitive domains.

## Vulnerability reporting
Do not disclose exploitable vulnerabilities in public issues. Use GitHub private vulnerability reporting / security advisories for this repository when available.

## Non-negotiable controls
- Least privilege and workspace isolation.
- Purpose-aware authorization for sensitive access.
- No secrets or production credentials in source control.
- AI outputs never become verified facts without an independent verification path.
- Recommendation is not binding authority.
- Sensitive state changes require actor, purpose, audit metadata and explicit state transitions.
- Critical write operations must be idempotent where replay is possible.
- Dependencies and source are continuously scanned.
- Security failures block merge when they invalidate a release gate.
