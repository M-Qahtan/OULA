package com.oula.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TransactionViewingRequest(
        @Min(0) long expectedVersion,
        Instant preferredAt,
        @Size(max = 500) String note,
        boolean humanConfirmed
) {
}
