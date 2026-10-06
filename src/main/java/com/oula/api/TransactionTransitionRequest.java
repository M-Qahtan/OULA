package com.oula.api;

import com.oula.transaction.TransactionStage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionTransitionRequest(
        @Min(0) long expectedVersion,
        @NotNull TransactionStage target,
        @NotBlank @Size(max = 500) String reason
) {
}
