package com.oula.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransactionCreateRequest(
        @NotNull UUID intentId,
        @NotNull UUID propertyId
) {
}
