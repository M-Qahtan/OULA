package com.oula.operations;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitWorkOrderCompletionCommand(
        BigDecimal actualCost,
        UUID completionEvidenceId,
        String completionNote
) {}
