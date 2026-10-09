package com.oula.leasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Lease(
        UUID id,
        UUID workspaceId,
        UUID propertyId,
        UUID landlordPartyId,
        UUID tenantPartyId,
        String leaseType,
        String status,
        Instant startsAt,
        Instant endsAt,
        BigDecimal rentAmount,
        String currency,
        String paymentFrequency,
        BigDecimal securityDeposit,
        UUID contractEvidenceId,
        String externalContractReference,
        String sourceType,
        UUID createdBy,
        Instant createdAt,
        UUID activatedBy,
        Instant activatedAt,
        UUID terminatedBy,
        Instant terminatedAt,
        String terminationReason,
        long version
) {}
