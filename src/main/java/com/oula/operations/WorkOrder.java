package com.oula.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrder(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID actionItemId,
        String category,
        String title,
        String scopeDescription,
        String status,
        UUID providerPartyId,
        BigDecimal estimatedCost,
        BigDecimal approvedBudget,
        BigDecimal actualCost,
        String currency,
        UUID approvalActorId,
        Instant approvedAt,
        Instant startedAt,
        Instant completionSubmittedAt,
        UUID completionEvidenceId,
        String completionNote,
        Instant completedAt,
        UUID createdBy,
        Instant createdAt,
        long version
) {}
