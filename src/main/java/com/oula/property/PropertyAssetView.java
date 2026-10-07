package com.oula.property;

import java.util.UUID;

public record PropertyAssetView(
        UUID propertyId,
        UUID workspaceId,
        String assetType,
        String district,
        Integer bedrooms,
        long version
) {
}
