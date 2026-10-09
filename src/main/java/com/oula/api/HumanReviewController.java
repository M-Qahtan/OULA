package com.oula.api;

import com.oula.advisory.*;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.function.Supplier;

@RestController
class HumanReviewController {
    private final HumanReviewService reviews;
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;

    HumanReviewController(HumanReviewService reviews, WorkspacePurposeAuthorizer authorizer,
                          IdempotencyService idempotency) {
        this.reviews=reviews;this.authorizer=authorizer;this.idempotency=idempotency;
    }

    @PostMapping("/v1/properties/{propertyId}/advisory-reviews")
    ResponseEntity<HumanReviewCase> capture(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CaptureRequest request,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.advisory.review.capture");
        String op="advisory.review.capture.v1";
        return mutation(w,key,op,propertyId,request,HttpStatus.CREATED,HumanReviewCase.class,
                ()->reviews.capture(access,propertyId,request.recommendationIndex(),
                        request.expectedRulesVersion(),request.expectedActionCode(),
                        request.expectedUnitId(),request.expectedLeaseId(),
                        DeterministicUuid.from(op,w,key)));
    }

    @PostMapping("/v1/advisory-reviews/{caseId}/decisions")
    ResponseEntity<HumanReviewEvent> decide(
            @PathVariable UUID caseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DecisionRequest request,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.advisory.review.decide");
        String op="advisory.review.decision.v1";
        return mutation(w,key,op,caseId,request,HttpStatus.CREATED,HumanReviewEvent.class,
                ()->reviews.decide(access,caseId,request.decision(),request.rationale(),
                        DeterministicUuid.from(op,w,key)));
    }

    @PostMapping("/v1/advisory-reviews/{caseId}/outcomes")
    ResponseEntity<HumanReviewEvent> observe(
            @PathVariable UUID caseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OutcomeRequest request,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.advisory.review.observe");
        String op="advisory.review.outcome.v1";
        return mutation(w,key,op,caseId,request,HttpStatus.CREATED,HumanReviewEvent.class,
                ()->reviews.observeOutcome(access,caseId,request.observedOutcome(),
                        request.rationale(),request.evidenceId(),
                        DeterministicUuid.from(op,w,key)));
    }

    @GetMapping("/v1/advisory-reviews/{caseId}")
    ResponseEntity<HumanReviewTimeline> timeline(
            @PathVariable UUID caseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.advisory.review.read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviews.timeline(access,caseId));
    }

    @GetMapping("/v1/properties/{propertyId}/advisory-feedback-summary")
    ResponseEntity<HumanFeedbackSummary> summary(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.advisory.review.read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviews.summary(access,propertyId));
    }

    private AccessContext authorize(Authentication auth,UUID w,String purpose,String scope) {
        return authorizer.require(auth,w,purpose,AccessPurpose.PROPERTY_MANAGEMENT,scope);
    }
    private <T> ResponseEntity<T> mutation(
            UUID w,String key,String op,UUID resource,Object request,HttpStatus status,
            Class<T> type,Supplier<T> action) {
        var result=idempotency.execute(w,key,op,
                RequestFingerprint.sha256(op,w,resource,request),status.value(),type,action);
        return ResponseEntity.status(status)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    record CaptureRequest(
            @Min(0) int recommendationIndex,
            @NotBlank String expectedRulesVersion,
            @NotBlank String expectedActionCode,
            @NotNull UUID expectedUnitId,
            UUID expectedLeaseId
    ) {}
    record DecisionRequest(@NotBlank String decision,
                           @NotBlank @Size(max=2000) String rationale) {}
    record OutcomeRequest(@NotBlank String observedOutcome,
                          @NotBlank @Size(max=2000) String rationale,
                          @NotNull UUID evidenceId) {}
}
