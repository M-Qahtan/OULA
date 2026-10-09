package com.oula.tenancy;
import java.time.Instant;
import java.util.UUID;
public record RentalUnit(UUID id, UUID workspaceId, UUID propertyId, String unitCode,
                         String status, UUID createdBy, Instant createdAt) {}
