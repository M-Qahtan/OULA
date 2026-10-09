package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Documentary observation; not independent bank settlement confirmation. */
public record RentEvidenceEntry(
        UUID id, UUID workspaceId, UUID leaseId, UUID installmentId,
        String entryType, BigDecimal amount, String currency, UUID evidenceId,
        String externalReference, UUID reversesEntryId, String note,
        UUID recordedBy, Instant recordedAt
) {}
