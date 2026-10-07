package com.oula.operations;

import java.time.Instant;

public record CreateObligationCommand(
        String obligationType,
        String title,
        Instant dueAt,
        String priority,
        String sourceType,
        String sourceReference
) {}
