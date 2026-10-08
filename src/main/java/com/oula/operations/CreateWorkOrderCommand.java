package com.oula.operations;

import java.math.BigDecimal;

public record CreateWorkOrderCommand(
        String category,
        String title,
        String scopeDescription,
        BigDecimal estimatedCost,
        String currency
) {}
