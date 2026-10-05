package com.oula.platform;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        return next(Clock.systemUTC());
    }

    static UUID next(Clock clock) {
        long unixMillis = clock.millis() & 0x0000FFFFFFFFFFFFL;
        long randomA = RANDOM.nextInt(1 << 12);
        long msb = (unixMillis << 16) | 0x7000L | randomA;

        long randomB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long lsb = 0x8000000000000000L | randomB;

        return new UUID(msb, lsb);
    }
}
