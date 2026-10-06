package com.oula.platform.idempotency;

record StoredIdempotencyRecord(
        String operation,
        String requestHash,
        String state,
        Integer responseStatus,
        String responseBody
) {
}
