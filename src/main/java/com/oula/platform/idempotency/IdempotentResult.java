package com.oula.platform.idempotency;

public record IdempotentResult<T>(T value, boolean replayed) {
}
