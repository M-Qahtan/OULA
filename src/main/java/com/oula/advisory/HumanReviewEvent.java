package com.oula.advisory;
import java.time.Instant;
import java.util.UUID;
public record HumanReviewEvent(
    UUID id, UUID workspaceId, UUID reviewCaseId, int sequenceNo,
    String eventType, String valueCode, String rationale,
    UUID evidenceId, UUID actorId, Instant recordedAt
) {}
