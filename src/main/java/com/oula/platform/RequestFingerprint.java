package com.oula.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

public final class RequestFingerprint {
    private RequestFingerprint() {
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
