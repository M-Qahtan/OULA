package com.oula.intelligence;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RealityGapReviewService {
    private final RealityGapRepository repository;
    private final EvidenceRegistry evidence;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public RealityGapReviewService(
            RealityGapRepository repository,
            EvidenceRegistry evidence,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.evidence = evidence;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public RealityGapView review(
            AccessContext access,
            UUID gapId,
            ReviewRealityGapCommand command,
            UUID correlationId
    ) {
        requireAccess(access);
        Objects.requireNonNull(gapId, "gapId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.causeCategory(), "causeCategory");
        Objects.requireNonNull(command.reviewStatus(), "reviewStatus");
        Objects.requireNonNull(command.calibrationStatus(), "calibrationStatus");
        if (command.rationale() == null || command.rationale().isBlank()) {
            throw new IllegalArgumentException("rationale is required");
        }
        if (command.reviewStatus() == RealityGapReviewStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("a review cannot remain pending");
        }
        if (command.calibrationStatus() == CalibrationStatus.UNASSESSED
                || command.calibrationStatus() == CalibrationStatus.CONSUMED) {
            throw new IllegalArgumentException("review may only mark CANDIDATE or EXCLUDED");
        }
        if (command.causeConfidence() != null
                && (command.causeConfidence() < 0.0 || command.causeConfidence() > 1.0)) {
            throw new IllegalArgumentException("causeConfidence must be between 0 and 1");
        }

        List<UUID> evidenceIds = command.evidenceIds() == null
                ? List.of()
                : List.copyOf(command.evidenceIds());
        if (evidenceIds.size() > 25) {
            throw new IllegalArgumentException("too many evidence references");
        }

        if (command.reviewStatus() == RealityGapReviewStatus.DISMISSED) {
            if (command.calibrationStatus() != CalibrationStatus.EXCLUDED) {
                throw new IllegalArgumentException("dismissed gaps must be excluded");
            }
        } else if (command.causeCategory() != ErrorCauseCategory.UNKNOWN_CAUSE
                && evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("non-UNKNOWN cause requires evidence");
        }

        if (command.calibrationStatus() == CalibrationStatus.CANDIDATE) {
            if (command.reviewStatus() != RealityGapReviewStatus.REVIEWED
                    || command.causeCategory() == ErrorCauseCategory.UNKNOWN_CAUSE
                    || evidenceIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "calibration candidate requires reviewed non-UNKNOWN cause with evidence"
                );
            }
        }

        if (!evidenceIds.isEmpty()) {
            evidence.requireAvailable(access.workspaceId(), evidenceIds);
        }

        RealityGapView gap = repository.findById(access.workspaceId(), gapId);
        if (gap.reviewStatus() != RealityGapReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Reality Gap already reviewed");
        }

        UUID reviewId = UuidV7.next();
        repository.insertReview(
                reviewId,
                access.workspaceId(),
                gapId,
                access.actorId(),
                command,
                evidenceIds,
                correlationId,
                clock.instant()
        );
        repository.applyReview(gapId, command);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("gapId", gapId);
        details.put("reviewId", reviewId);
        details.put("causeCategory", command.causeCategory().name());
        details.put("reviewStatus", command.reviewStatus().name());
        details.put("calibrationStatus", command.calibrationStatus().name());
        details.put("evidenceCount", evidenceIds.size());

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_REALITY_GAP_REVIEWED",
                "RealityGap",
                gapId,
                correlationId,
                details
        );
        outbox.append(
                "intelligence.reality_gap.reviewed.v1",
                "RealityGap",
                gapId,
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );

        return repository.findById(access.workspaceId(), gapId);
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        Objects.requireNonNull(access.purpose(), "purpose");
    }
}
