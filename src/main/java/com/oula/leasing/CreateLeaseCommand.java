package com.oula.leasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateLeaseCommand(
        UUID landlordPartyId,
        UUID tenantPartyId,
        String leaseType,
        Instant startsAt,
        Instant endsAt,
        BigDecimal rentAmount,
        String currency,
        String paymentFrequency,
        BigDecimal securityDeposit,
        String externalContractReference,
        String sourceType
) {}
