package com.oula.advisory;

import com.oula.documents.VerifiedDocumentEvidenceService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class HumanReviewService {
    private static final Set<String> DECISIONS =
            Set.of("ACCEPT_FOR_REVIEW", "DECLINE", "DEFER");
    private static final Set<String> OUTCOMES =
            Set.of("IMPROVEMENT_OBSERVED","NO_CHANGE_OBSERVED",
                   "DETERIORATION_OBSERVED","INCONCLUSIVE");

    private final HumanReviewRepository repository;
    private final RentalLifecycleAdvisoryService rentalAdvice;
    private final VerifiedDocumentEvidenceService evidence;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final JsonMapper json;
    private final Clock clock = Clock.systemUTC();

    public HumanReviewService(HumanReviewRepository repository,
            RentalLifecycleAdvisoryService rentalAdvice,
            VerifiedDocumentEvidenceService evidence,
            AuditWriter audit, OutboxWriter outbox, JsonMapper json) {
        this.repository = repository;
        this.rentalAdvice = rentalAdvice;
        this.evidence = evidence;
        this.audit = audit;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    public HumanReviewCase capture(AccessContext access, UUID propertyId,
            int recommendationIndex, String expectedRulesVersion, String expectedActionCode,
            UUID expectedUnitId, UUID expectedLeaseId, UUID correlationId) {
        requireHuman(access);
        Objects.requireNonNull(propertyId,"propertyId");
        Objects.requireNonNull(expectedUnitId,"expectedUnitId");
        Objects.requireNonNull(correlationId,"correlationId");
        if (recommendationIndex < 0 || recommendationIndex >= 2500)
            throw new IllegalArgumentException("recommendation index invalid");
        RentalLifecycleAdvisory current = rentalAdvice.recommend(access,propertyId);
        if (!current.rulesVersion().equals(expectedRulesVersion))
            throw new IllegalStateException("advisory rules version changed");
        if (recommendationIndex >= current.recommendations().size())
            throw new IllegalStateException("recommendation no longer exists");
        AdvisoryItem selected = current.recommendations().get(recommendationIndex);
        UUID unitId = metricUuid(selected,"unitId",true);
        UUID leaseId = metricUuid(selected,"leaseId",false);
        if (!selected.actionCode().equals(expectedActionCode)
                || !expectedUnitId.equals(unitId)
                || !Objects.equals(expectedLeaseId,leaseId)
                || !"HUMAN_REVIEW_REQUIRED".equals(selected.executionGate())) {
            throw new IllegalStateException("recommendation selection changed; re-read source");
        }
        // Frozen provenance: subsequent recommendation changes never rewrite this case.
        String metrics;
        try { metrics=json.writeValueAsString(selected.observedEvidence()); }
        catch (Exception ex) { throw new IllegalStateException("failed to serialize source",ex); }
        Instant now=clock.instant();
        String fingerprint=RequestFingerprint.sha256(access.workspaceId(),propertyId,
                current.rulesVersion(),current.generatedAt(),selected.dimension(),
                selected.actionCode(),unitId,leaseId,metrics);
        HumanReviewCase review=new HumanReviewCase(UuidV7.next(),access.workspaceId(),
                propertyId,unitId,leaseId,current.rulesVersion(),selected.dimension(),
                selected.actionCode(),selected.priority(),current.generatedAt(),
                metrics,fingerprint,access.actorId(),now);
        repository.insertCase(review);
        emit(access,"ADVISORY_HUMAN_REVIEW_CAPTURED","advisory.review.captured.v1",
                review.id(),correlationId,Map.of("propertyId",propertyId,
                        "rulesVersion",review.rulesVersion(),
                        "actionCode",review.actionCode()));
        return review;
    }

    @Transactional
    public HumanReviewEvent decide(AccessContext access,UUID caseId,
            String decision,String rationale,UUID correlationId) {
        requireHuman(access);
        if(!DECISIONS.contains(decision))
            throw new IllegalArgumentException("unsupported review decision");
        requireNote(rationale);
        Objects.requireNonNull(correlationId,"correlationId");
        repository.load(access.workspaceId(),caseId,true);
        List<HumanReviewEvent> history=repository.history(access.workspaceId(),caseId);
        // A decision is a human disposition, not authorization to execute.
        if (!history.isEmpty() && history.getLast().eventType().equals("OUTCOME_OBSERVATION"))
            throw new IllegalStateException("feedback already exists: open a new review case");
        HumanReviewEvent event=new HumanReviewEvent(UuidV7.next(),access.workspaceId(),
                caseId,history.size()+1,"DECISION",decision,rationale.strip(),
                null,access.actorId(),clock.instant());
        repository.insertEvent(event);
        emit(access,"ADVISORY_HUMAN_DECISION_RECORDED","advisory.review.decision.v1",
                event.id(),correlationId,Map.of("reviewCaseId",caseId,"decision",decision));
        return event;
    }

    @Transactional
    public HumanReviewEvent observeOutcome(AccessContext access,UUID caseId,
            String observedOutcome,String rationale,UUID verifiedEvidenceId,
            UUID correlationId) {
        requireHuman(access);
        if(!OUTCOMES.contains(observedOutcome))
            throw new IllegalArgumentException("unsupported observation");
        requireNote(rationale);
        Objects.requireNonNull(verifiedEvidenceId,"verifiedEvidenceId");
        Objects.requireNonNull(correlationId,"correlationId");
        repository.load(access.workspaceId(),caseId,true);
        List<HumanReviewEvent> history=repository.history(access.workspaceId(),caseId);
        boolean accepted=history.stream().anyMatch(e->e.eventType().equals("DECISION")
                && e.valueCode().equals("ACCEPT_FOR_REVIEW"));
        if (!accepted || (!history.isEmpty()
                && history.stream().filter(e->e.eventType().equals("DECISION"))
                        .reduce((a,b)->b).map(e->!e.valueCode().equals("ACCEPT_FOR_REVIEW"))
                        .orElse(true))) {
            throw new IllegalStateException("latest human decision must accept review");
        }
        evidence.requireVerified(access.workspaceId(),verifiedEvidenceId,"ADVISORY_OUTCOME");
        HumanReviewEvent event=new HumanReviewEvent(UuidV7.next(),access.workspaceId(),
                caseId,history.size()+1,"OUTCOME_OBSERVATION",observedOutcome,
                rationale.strip(),verifiedEvidenceId,access.actorId(),clock.instant());
        repository.insertEvent(event);
        emit(access,"ADVISORY_HUMAN_OUTCOME_OBSERVED","advisory.review.outcome_observed.v1",
                event.id(),correlationId,Map.of("reviewCaseId",caseId,
                        "observedOutcome",observedOutcome,"evidenceId",verifiedEvidenceId));
        return event;
    }

    @Transactional(readOnly=true)
    public HumanReviewTimeline timeline(AccessContext access, UUID caseId) {
        requireHuman(access);
        HumanReviewCase review=repository.load(access.workspaceId(),caseId,false);
        return new HumanReviewTimeline(review,repository.history(access.workspaceId(),caseId));
    }

    @Transactional(readOnly=true)
    public HumanFeedbackSummary summary(AccessContext access, UUID propertyId) {
        requireHuman(access);
        Objects.requireNonNull(propertyId,"propertyId");
        return repository.summary(access.workspaceId(),propertyId);
    }

    private UUID metricUuid(AdvisoryItem item,String name,boolean required) {
        String value=item.observedEvidence().stream().filter(m->m.name().equals(name))
                .map(AdvisoryItem.EvidenceMetric::value).findFirst().orElse(null);
        if(value == null) {
            if(required) throw new IllegalStateException("recommendation lacks unit source");
            return null;
        }
        return UUID.fromString(value);
    }

    private void requireHuman(AccessContext access) {
        Objects.requireNonNull(access,"access");
        if(access.purpose()!=AccessPurpose.PROPERTY_MANAGEMENT)
            throw new SecurityException("PROPERTY_MANAGEMENT purpose required");
    }
    private void requireNote(String value) {
        if(value==null||value.isBlank()||value.length()>2000)
            throw new IllegalArgumentException("rationale required (max 2000)");
    }

    private void emit(AccessContext access,String action,String eventType,UUID id,
                      UUID correlationId, Map<String, ?> payload) {
        audit.append(access.workspaceId(),access.actorId(),access.subject(),
                access.purpose().name(),action,"HumanAdvisoryReview",id,correlationId,payload);
        outbox.append(eventType,"HumanAdvisoryReview",id,access.workspaceId(),
                correlationId,correlationId,payload);
    }
}
