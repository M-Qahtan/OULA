package com.oula.platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.StringJoiner;
import java.util.UUID;

public final class DeterministicUuid {
    private DeterministicUuid() {
    }

    public static UUID from(String namespace, Object... components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringJoiner canonical = new StringJoiner("\u001f");
            canonical.add(namespace);
            for (Object component : components) {
                canonical.add(String.valueOf(component));
            }

            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x80);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);

            ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, 16);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
