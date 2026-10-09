package com.oula.tenancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record Lease(UUID id, UUID workspaceId, UUID unitId, UUID landlordPartyId,
                    UUID tenantPartyId, LocalDate startOn, LocalDate endOn,
                    BigDecimal periodicRent, String currency, int rentEveryMonths,
                    String status, UUID signingEvidenceId, Instant signedAt,
                    Instant activatedAt, UUID terminationEvidenceId, Instant endedAt,
                    UUID createdBy, Instant createdAt, long version) {}
