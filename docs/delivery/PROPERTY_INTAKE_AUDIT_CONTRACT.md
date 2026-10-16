# Property and private listing provenance/audit contract

- Created assets are private to their authorized workspace; creation is neither title verification nor publication.
- Intake writes `property.fact` rows with `DECLARED` truth, `DEMO_FIXTURE` or `USER_DECLARATION` source.
- Draft listings stay `DRAFT` and are never distributed to a public marketplace from this API.
- Each successful first-time create writes a corresponding `platform.audit_log` and `platform.outbox_event` record **within the same transaction** as the property/listing mutation and idempotency receipt.
- Replaying the same idempotency key returns the previous receipt without creating a second event or side effect.
- An error in the write, audit or outbox path must roll back the mutation, claim and audit/event together.
- Audit payloads deliberately contain state/provenance metadata, not sensitive owner identities or raw personal context.
- External publishing, identity verification, licensed brokerage actions, government registries and payments require separate approved workflows.
