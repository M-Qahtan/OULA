# OULA Security Policy

OULA treats identity, property evidence, transactions, consent, and AI-derived recommendations as security-sensitive domains.

## Reporting a vulnerability
Do not disclose exploitable vulnerabilities in a public issue. Use GitHub's private vulnerability reporting/security advisory flow for this repository when available.

## Non-negotiable controls
- Least privilege and workspace isolation.
- No secrets in source control.
- AI outputs never become verified facts without a verification path.
- Sensitive state changes require authorization, auditability, and idempotency where applicable.
- Dependencies and source code are scanned in CI.
