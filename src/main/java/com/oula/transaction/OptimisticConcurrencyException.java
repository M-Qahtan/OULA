package com.oula.transaction;

import java.util.UUID;

public final class OptimisticConcurrencyException extends RuntimeException {
    public OptimisticConcurrencyException(UUID transactionId, long expectedVersion) {
        super("transaction version conflict: id=" + transactionId + ", expectedVersion=" + expectedVersion);
    }
}
