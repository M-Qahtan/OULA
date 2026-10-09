package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record CreateLeaseCommand(
        UUID landlordPartyId, UUID tenantPartyId, LocalDate startOn,
        LocalDate endOn, BigDecimal periodicRent, String currency, int rentEveryMonths
) {}
