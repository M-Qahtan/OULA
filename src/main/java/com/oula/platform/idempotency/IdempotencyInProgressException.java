package com.oula.platform.idempotency;

public final class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException(String key) {
        super("idempotent operation is still in progress: " + key);
    }
}
