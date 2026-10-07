package com.oula.property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PropertyPassport(
        UUID propertyId,
        UUID workspaceId,
        String assetType,
        String district,
        Integer bedrooms,
        BigDecimal askingPrice,
        List<PropertyPassportFact> facts,
        PropertyStateSnapshot latestState,
        double verifiedFactCoverage,
        Instant generatedAt
) {
    public PropertyPassport {
        facts = List.copyOf(facts);
    }
}
