package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.intelligence.AssumptionSensitivity;
import com.oula.intelligence.DecisionIntelligenceService;
import com.oula.intelligence.DecisionView;
import com.oula.intelligence.EvidenceType;
import com.oula.intelligence.GenerateRecommendationCommand;
import com.oula.intelligence.OutcomeView;
import com.oula.intelligence.RecommendationExplanation;
import com.oula.intelligence.RecommendationView;
import com.oula.intelligence.RealityGapService;
import com.oula.intelligence.RealityGapView;
import com.oula.intelligence.RecordDecisionCommand;
import com.oula.intelligence.RecordOutcomeCommand;
import com.oula.intelligence.RegisterAssumptionCommand;
import com.oula.intelligence.RegisterEvidenceCommand;
import com.oula.intelligence.VerificationStatus;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@RestController
@RequestMapping("/v1/intelligence")
class DecisionIntelligenceController {
    private static final String PURPOSE = "PROPERTY_DECISION_SUPPORT";

    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final DecisionIntelligenceService intelligence;
    private final RealityGapService realityGaps;

    DecisionIntelligenceController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            DecisionIntelligenceService intelligence,
            RealityGapService realityGaps
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.intelligence = intelligence;
        this.realityGaps = realityGaps;
    }

    @PostMapping("/evidence")
    ResponseEntity<IntelligenceIdResponse> registerEvidence(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EvidenceRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.evidence.register.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.evidence.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                request.evidenceType(),
                request.sourceType(),
                request.sourceIdentity(),
                request.contentReference(),
                request.contentHash(),
                request.verificationStatus(),
                request.capturedAt(),
                request.validUntil(),
                request.jurisdiction()
        );
        UUID correlationId = correlation(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                IntelligenceIdResponse.class,
                () -> new IntelligenceIdResponse(intelligence.registerEvidence(
                        access,
                        new RegisterEvidenceCommand(
                                request.evidenceType(),
                                request.sourceType(),
                                request.sourceIdentity(),
                                request.contentReference(),
                                request.contentHash(),
                                request.verificationStatus(),
                                request.capturedAt(),
                                request.validUntil(),
                                request.jurisdiction()
                        ),
                        correlationId
                ))
        );

        return created(result.value(), result.replayed());
    }

    @PostMapping("/assumptions")
    ResponseEntity<IntelligenceIdResponse> registerAssumption(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AssumptionRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.assumption.register.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.assumption.write"
        );

        Map<String, Object> canonicalValue = request.value() == null
                ? Map.of()
                : new TreeMap<>(request.value());

        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                request.statement(),
                canonicalValue,
                request.source(),
                request.reason(),
                request.confidence(),
                request.sensitivity(),
                request.validUntil()
        );
        UUID correlationId = correlation(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                IntelligenceIdResponse.class,
                () -> new IntelligenceIdResponse(intelligence.registerAssumption(
                        access,
                        new RegisterAssumptionCommand(
                                request.statement(),
                                canonicalValue,
                                request.source(),
                                request.reason(),
                                request.confidence(),
                                request.sensitivity(),
                                request.validUntil()
                        ),
                        correlationId
                ))
        );

        return created(result.value(), result.replayed());
    }

    @PostMapping("/recommendations")
    ResponseEntity<RecommendationView> generateRecommendation(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecommendationRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.recommendation.generate.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.recommend"
        );

        List<UUID> evidenceIds = sorted(request.evidenceIds());
        List<UUID> assumptionIds = sorted(request.assumptionIds());
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                request.matchRunId(),
                evidenceIds,
                assumptionIds,
                request.validHours()
        );
        UUID correlationId = correlation(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                RecommendationView.class,
                () -> intelligence.generateRecommendation(
                        access,
                        new GenerateRecommendationCommand(
                                request.matchRunId(),
                                evidenceIds,
                                assumptionIds,
                                request.validHours()
                        ),
                        correlationId
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/recommendations/{recommendationId}/explain")
    RecommendationExplanation explainRecommendation(
            @PathVariable UUID recommendationId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.read"
        );
        return intelligence.explain(access, recommendationId);
    }

    @PostMapping("/recommendations/{recommendationId}/decisions")
    ResponseEntity<DecisionView> recordDecision(
            @PathVariable UUID recommendationId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DecisionRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.decision.record.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.decide"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                recommendationId,
                request.selectedPropertyId(),
                request.overrideReason()
        );
        UUID correlationId = correlation(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                DecisionView.class,
                () -> intelligence.recordDecision(
                        access,
                        new RecordDecisionCommand(
                                recommendationId,
                                request.selectedPropertyId(),
                                request.overrideReason()
                        ),
                        correlationId
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/decisions/{decisionId}/outcomes")
    ResponseEntity<OutcomeView> recordOutcome(
            @PathVariable UUID decisionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OutcomeRequest request,
            Authentication authentication
    ) {
        String operation = "intelligence.outcome.record.v1";
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.outcome.write"
        );

        List<UUID> evidenceIds = sorted(request.evidenceIds());
        String fingerprint = RequestFingerprint.sha256(
                operation,
                workspaceId,
                decisionId,
                request.actualCommuteMinutes(),
                request.satisfactionScore(),
                request.confidence(),
                evidenceIds,
                request.observedAt()
        );
        UUID correlationId = correlation(operation, workspaceId, idempotencyKey);

        var result = idempotency.execute(
                workspaceId,
                idempotencyKey,
                operation,
                fingerprint,
                HttpStatus.CREATED.value(),
                OutcomeView.class,
                () -> intelligence.recordOutcome(
                        access,
                        new RecordOutcomeCommand(
                                decisionId,
                                request.actualCommuteMinutes(),
                                request.satisfactionScore(),
                                request.confidence(),
                                evidenceIds,
                                request.observedAt()
                        ),
                        correlationId
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/outcomes/{outcomeId}/reality-gaps")
    List<RealityGapView> realityGaps(
            @PathVariable UUID outcomeId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(
                authentication,
                workspaceId,
                requestedPurpose,
                "oula.intelligence.read"
        );
        return realityGaps.forOutcome(access, outcomeId);
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String requestedPurpose,
            String scope
    ) {
        return authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                scope
        );
    }

    private UUID correlation(String operation, UUID workspaceId, String idempotencyKey) {
        return DeterministicUuid.from(operation, workspaceId, idempotencyKey);
    }

    private List<UUID> sorted(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> copy = new ArrayList<>(ids);
        copy.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(copy);
    }

    private ResponseEntity<IntelligenceIdResponse> created(
            IntelligenceIdResponse response,
            boolean replayed
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    record EvidenceRequest(
            @NotNull EvidenceType evidenceType,
            @NotBlank @Size(max = 80) String sourceType,
            @Size(max = 255) String sourceIdentity,
            String contentReference,
            @Size(max = 128) String contentHash,
            @NotNull VerificationStatus verificationStatus,
            Instant capturedAt,
            Instant validUntil,
            @Size(max = 80) String jurisdiction
    ) {
    }

    record AssumptionRequest(
            @NotBlank String statement,
            Map<String, Object> value,
            @NotBlank @Size(max = 255) String source,
            @NotBlank String reason,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
            @NotNull AssumptionSensitivity sensitivity,
            Instant validUntil
    ) {
    }

    record RecommendationRequest(
            @NotNull UUID matchRunId,
            @NotNull List<UUID> evidenceIds,
            List<UUID> assumptionIds,
            @Min(1) @jakarta.validation.constraints.Max(720) int validHours
    ) {
    }

    record DecisionRequest(
            @NotNull UUID selectedPropertyId,
            String overrideReason
    ) {
    }

    record OutcomeRequest(
            @Min(0) int actualCommuteMinutes,
            @DecimalMin("0.0") @DecimalMax("100.0") double satisfactionScore,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
            @NotNull List<UUID> evidenceIds,
            Instant observedAt
    ) {
    }
}
