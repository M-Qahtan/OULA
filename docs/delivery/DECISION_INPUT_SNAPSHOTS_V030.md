# OULA Decision Input Snapshots (V030)

Every newly generated material recommendation now persists an immutable-by-application
**historical snapshot** in the same transaction as the recommendation, linked evidence
and assumptions. It includes algorithm/model version, ranked property alternatives
and their explanation/score, evidence IDs and exact admitted content hashes,
source-attestation IDs, and assumption payloads. Arrays have explicit stable order.

The payload is preserved as serialized UTF-8 text plus versioned local SHA-256
fingerprint, rather than recomputing a digest from newly formatted JSON after a
database upgrade. PostgreSQL storage is not WORM: privileged administrators can
modify these rows and the digest. External signatures and retention/erasure policy
are separate production gates.

This captures *the model's decision-time inputs available in the current API*.
It does not include full external geospatial observations or all property facts,
does not guarantee a bit-for-bit recomputation of LifeFit from the snapshot,
and is **not** evidence of calibrated market accuracy. Future work should attach
the source-verified property claims, canonical inputs and measured outcomes.
Legacy recommendations do not receive a fabricated retrospective snapshot.

No new public read endpoint: potentially sensitive household assumption values
must not be exported without scoped authorization and privacy review.
