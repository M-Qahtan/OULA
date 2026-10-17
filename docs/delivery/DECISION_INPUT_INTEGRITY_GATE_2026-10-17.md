# OULA — Decision-input integrity admission (V031 logic-only)

The V030 serialized input snapshot is now checked **before a new human decision
is recorded**. The check is performed after the recommendation is retrieved
within its verified workspace and inside the same Spring DB transaction as
decision creation. SELECT FOR SHARE on the snapshot prevents a concurrent
database update/delete from racing between verification and commit.

The read-only-in-behavior API
`GET /v1/intelligence/recommendations/{id}/input-integrity` requires
`oula.intelligence.read`, PROPERTY_DECISION_SUPPORT, and an authorized
workspace, and returns only local status/schema/hash (never sensitive raw
household assumptions). No-store caching. Missing historical snapshots,
corrupt hashes, or unknown schema block *new* material human decisions;
historical recorded decisions and idempotent receipts are not rewritten.

`MATCHES_STORED_DIGEST` means the stored serialized UTF-8 bytes match the
locally stored SHA-256 digest with the V1 domain separator, nothing more.
It is **not** a cryptographic signature, external source attestation, trusted
timestamp, WORM, independent audit or exact reproducible LifeFit computation.
A privileged DB actor can modify payload and hash together; tamper-resistant
external signing and database role segregation remain pending.

Policy change: legacy recommendations without snapshots cannot support new
material decisions and need explicit review/re-evaluation. Do not backfill
fictional evidence or regenerate historical data from mutable live sources.
