package com.oula.api;

import com.oula.intent.IntentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

/** All requirements are first-party declarations, never verified property facts. */
public record IntentCreateRequest(
        @NotNull IntentType intentType,
        @NotNull @DecimalMin("0.01") BigDecimal budgetMax,
        @NotNull @Min(0) Integer minimumBedrooms,
        @NotNull @Size(max = 12) Set<@NotBlank @Size(max = 100) String> preferredDistricts
) {}
