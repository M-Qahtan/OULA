package com.oula.advisory;
import java.time.Instant;
import java.util.UUID;
public record HumanReviewCase(
    UUID id, UUID workspaceId, UUID propertyId, UUID unitId, UUID leaseId,
    String rulesVersion, String dimension, String actionCode, String initialPriority,
    Instant sourceGeneratedAt, String sourceMetricsJson, String sourceFingerprint,
    UUID capturedBy, Instant capturedAt
) {}
