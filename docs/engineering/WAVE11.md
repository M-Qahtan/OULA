# OULA Engineering Wave 11 — Integration Trust Fabric

## Goal

Provide a secure, provider-neutral boundary for future government, registry, bank, insurer, identity, mapping, service-provider and data-provider integrations without allowing external transport details to leak into OULA domain semantics.

## Trust chain

Transport Adapter → VerifiedExternalPrincipal → Active Verified Partner → Active Integration Contract → Purpose / Operation / Resource Match → Data-Class Subset → Replay Guard → Metadata Receipt → Domain Event.

## Delivered

- workspace-scoped integration partner registry;
- explicit partner verification with evidence reference;
- auth-mode and credential-reference metadata without storing raw secrets;
- dedicated INTEGRATION_OPERATIONS purpose;
- exact inbound/outbound integration contracts;
- explicit purpose limitation;
- explicit allowed data classes;
- inbound principal freshness and binding checks;
- replay protection by partner + external event id;
- idempotent same-event/same-hash receipt replay;
- hard conflict for same event id with changed content/context;
- metadata/hash/reference storage instead of raw inbound payload;
- provider-neutral PREPARED outbound request;
- Audit + Outbox events;
- management API for partners/contracts/outbound preparation;
- no public raw webhook endpoint before a real mTLS/JWS authentication adapter exists.

## Invariants

1. External partner identity != OULA user identity.
2. Integration partner registration != verification.
3. credential_reference != stored credential secret.
4. Transport authentication must happen before VerifiedExternalPrincipal is constructed.
5. VerifiedExternalPrincipal must be fresh and match configured partner/auth/credential metadata.
6. No active matching contract means reject.
7. Requested data classes must be a subset of contract data classes.
8. Raw inbound payload is not stored in integration.inbound_receipt.
9. Same external event id + same context/hash is idempotent.
10. Same external event id + changed context/hash is replay conflict.
11. PREPARED outbound request != network dispatch.
12. External provider never owns OULA canonical domain meaning.

## Deliberate boundary

Wave 11 does not expose an unauthenticated webhook. A future adapter wave must implement a concrete mTLS, JWS, signed-webhook or OAuth client authentication mechanism and only then construct VerifiedExternalPrincipal.

## Exit gate

Wave 11 may merge only after:
- Flyway V001 → V015 on PostgreSQL/PostGIS;
- all existing regressions;
- partner verification test;
- inbound contract/data-minimization test;
- replay/idempotency conflict test;
- no-raw-payload storage assertion;
- outbound contract/data-minimization test;
- integration API purpose/scope test;
- Spring Modulith verification;
- CodeQL green.
