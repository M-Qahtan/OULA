package com.oula.platform;

import com.oula.iam.AccessContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

public final class RequestFingerprint {
    private RequestFingerprint() {
    }

    /**
     * Bind a completed command receipt to the verified actor, workspace and purpose.
     * A shared workspace key alone must not reveal another actor's replay response.
     *
     * Versioned deliberately: previously unbound receipts conflict rather than
     * becoming cross-actor replayable during a development-data migration.
     */
    public static String scoped(AccessContext access, String requestHash) {
        if (access == null || access.actorId() == null || access.workspaceId() == null
                || access.purpose() == null || requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("Verified actor context and request hash are required");
        }
        return sha256("OULA_ACTOR_SCOPED_IDEMPOTENCY_V1", access.workspaceId(),
                access.actorId(), access.purpose().name(), requestHash);
    }

    public static String sha256(Object... components) {
        try {
            StringJoiner canonical = new StringJoiner("\u001f");
            for (Object component : components) {
                canonical.add(String.valueOf(component));
            }
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
