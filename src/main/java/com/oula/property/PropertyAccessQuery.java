package com.oula.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PropertyAccessQuery {
    private final PropertyRepository repository;

    public PropertyAccessQuery(PropertyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PropertyAssetView requireOwned(UUID workspaceId, UUID propertyId) {
        return repository.findOwned(workspaceId, propertyId);
    }
}
