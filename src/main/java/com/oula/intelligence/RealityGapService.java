package com.oula.intelligence;

import com.oula.iam.AccessContext;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RealityGapService {
    private final RealityGapRepository repository;
    private final RealityGapPolicyRepository policies;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final RealityGapCalculator calculator = new RealityGapCalculator();

    public RealityGapService(
            RealityGapRepository repository,
            RealityGapPolicyRepository policies,
            AuditWriter audit,
            OutboxWriter outbox
    ) {
        this.repository = repository;
        this.policies = policies;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public RealityGapView detect(
            AccessContext access,
            UUID outcomeId,
            UUID decisionId,
            UUID recommendationId,
            UUID modelVersionId,
            String metricKey,
            String unit,
            BigDecimal expectedValue,
            BigDecimal actualValue,
            UUID correlationId,
            Instant detectedAt
    ) {
        requireAccess(access);
        Objects.requireNonNull(outcomeId, "outcomeId");
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(modelVersionId, "modelVersionId");
        Objects.requireNonNull(metricKey, "metricKey");
        Objects.requireNonNull(expectedValue, "expectedValue");
        Objects.requireNonNull(actualValue, "actualValue");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(detectedAt, "detectedAt");

        RealityGapPolicy policy = policies.activeFor(metricKey);
        RealityGapMeasurement measurement = calculator.measure(
                expectedValue, actualValue, policy
        );

        RealityGapView gap = new RealityGapView(
                UuidV7.next(),
                outcomeId,
                decisionId,
                recommendationId,
                modelVersionId,
                metricKey,
                unit,
                measurement.expectedValue(),
                measurement.actualValue(),
                measurement.signedError(),
                measurement.absoluteError(),
                measurement.relativeError(),
                policy.key(),
                policy.version(),
                measurement.classification(),
                ErrorCauseCategory.UNKNOWN_CAUSE,
                RealityGapReviewStatus.PENDING_REVIEW,
                CalibrationStatus.UNASSESSED,
                detectedAt
        );
        repository.insert(gap, access.workspaceId(), correlationId);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcomeId", outcomeId);
        details.put("decisionId", decisionId);
        details.put("recommendationId", recommendationId);
        details.put("modelVersionId", modelVersionId);
        details.put("metricKey", metricKey);
        details.put("expectedValue", measurement.expectedValue());
        details.put("actualValue", measurement.actualValue());
        details.put("signedError", measurement.signedError());
        details.put("absoluteError", measurement.absoluteError());
        if (measurement.relativeError() != null) {
            details.put("relativeError", measurement.relativeError());
        }
        details.put("policyKey", policy.key());
        details.put("policyVersion", policy.version());
        details.put("classification", measurement.classification().name());
        details.put("causeCategory", ErrorCauseCategory.UNKNOWN_CAUSE.name());
        details.put("calibrationStatus", CalibrationStatus.UNASSESSED.name());

        audit.append(
                access.workspaceId(),
                access.actorId(),
                access.subject(),
                access.purpose().name(),
                "INTELLIGENCE_REALITY_GAP_DETECTED",
                "RealityGap",
                gap.gapId(),
                correlationId,
                details
        );
        outbox.append(
                "intelligence.reality_gap.detected.v1",
                "RealityGap",
                gap.gapId(),
                access.workspaceId(),
                correlationId,
                correlationId,
                details
        );

        return gap;
    }

    @Transactional(readOnly = true)
    public List<RealityGapView> forOutcome(AccessContext access, UUID outcomeId) {
        requireAccess(access);
        return repository.findByOutcome(
                access.workspaceId(),
                Objects.requireNonNull(outcomeId, "outcomeId")
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        Objects.requireNonNull(access.purpose(), "purpose");
    }
}
