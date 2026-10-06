package com.oula.integration;
import java.time.Instant;
import java.util.UUID;
public record ExternalReference(UUID id, String providerCode, String internalResourceType, UUID internalResourceId, String externalResourceType, String externalResourceId, Instant lastVerifiedAt) {
  public ExternalReference {
    if (id == null || providerCode == null || internalResourceType == null || internalResourceId == null || externalResourceType == null || externalResourceId == null) throw new NullPointerException();
  }
}
