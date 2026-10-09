package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Amounts reflect verified documentary records, not externally reconciled cash. */
public record RentEvidencePosition(
        UUID installmentId, UUID leaseId, LocalDate dueOn, String currency,
        BigDecimal contractualDue, BigDecimal documentaryNetReceived,
        BigDecimal documentaryGap, String evidenceStatus
) {}
