package com.oula.api;

import com.oula.iam.ConsentCode;
import com.oula.iam.ConsentDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentDecisionRequest(
        @NotNull ConsentCode consentCode,
        @NotNull ConsentDecision decision,
        @Size(max = 500) String reason
) {
}
