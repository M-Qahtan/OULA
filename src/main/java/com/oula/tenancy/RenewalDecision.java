package com.oula.tenancy;
import java.time.Instant;
import java.util.UUID;
public record RenewalDecision(UUID id, UUID workspaceId, UUID leaseId, String decision,
                              String rationale, UUID evidenceId, UUID actorId,
                              Instant recordedAt) {}
