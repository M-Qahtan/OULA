package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record RentInstallment(UUID id, UUID workspaceId, UUID leaseId,
                              LocalDate dueOn, BigDecimal amount, String currency,
                              String status, Instant createdAt) {}
