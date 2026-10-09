package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record RentalFinancialSummary(
        UUID leaseId, LocalDate asOfDate, String currency,
        BigDecimal totalContractualDue, BigDecimal netDocumentaryReceipts,
        BigDecimal uncoveredDueThroughDate, int overdueInstallments,
        List<RentEvidencePosition> installments
) {}
