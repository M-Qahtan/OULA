package com.oula.intelligence;

import java.util.UUID;

/**
 * Checks local saved bytes against a stored digest, NOT external source authority
 * and NOT replay/calibration certification. Raw household inputs are never returned.
 */
public record SnapshotIntegrityView(
        UUID recommendationId,
        Status status,
        String schemaVersion,
        String localDigest
) {
    public enum Status {
        MATCHES_STORED_DIGEST,
        DIGEST_MISMATCH,
        MISSING,
        UNSUPPORTED_SCHEMA
    }
}
