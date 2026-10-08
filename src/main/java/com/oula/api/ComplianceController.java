package com.oula.api;

import com.oula.compliance.*;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/compliance")
class ComplianceController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final ComplianceKernel kernel;

    ComplianceController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            ComplianceKernel kernel
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.kernel = kernel;
    }

    @PostMapping("/evaluate")
    ResponseEntity<PolicyDecision> evaluate(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PolicyEvaluationRequest request,
            Authentication authentication
    ) {
        String operation = "compliance.policy.evaluate.v1";
        AccessContext access = authorizePurpose(
                authentication, workspaceId, purpose, "oula.compliance.evaluate"
        );
        PolicyRequest policyRequest = request.toPolicyRequest();
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, purpose, request.action(),
                request.resourceType(), request.resourceId(), request.jurisdiction(),
                request.amount(), request.currency(), request.verificationPresent(),
                request.evidencePresent(), request.approvalRequestId(), request.context()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.OK.value(),
                PolicyDecision.class,
                () -> kernel.evaluate(access, policyRequest, correlation)
        );
        return ok(result.replayed(), result.value());
    }

    @PostMapping("/policy-rules")
    ResponseEntity<PolicyRule> createRule(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PolicyRuleRequest request,
            Authentication authentication
    ) {
        String operation = "compliance.policy_rule.create.v1";
        AccessContext access = authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, "oula.compliance.policy.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.policyKey(), request.version(),
                request.appliesPurpose(), request.actionPattern(),
                request.resourceTypePattern(), request.jurisdictionPattern(),
                request.ruleEffect(), request.maxAmount(), request.currency(),
                request.requiresVerification(), request.requiresEvidence(),
                request.priority(), request.effectiveFrom(), request.effectiveUntil()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                PolicyRule.class,
                () -> kernel.createRule(
                        access,
                        new CreatePolicyRuleCommand(
                                request.policyKey(),
                                request.version(),
                                request.appliesPurpose(),
                                request.actionPattern(),
                                request.resourceTypePattern(),
                                request.jurisdictionPattern(),
                                request.ruleEffect(),
                                request.maxAmount(),
                                request.currency(),
                                request.requiresVerification(),
                                request.requiresEvidence(),
                                request.priority(),
                                request.effectiveFrom(),
                                request.effectiveUntil()
                        ),
                        correlation
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/approval-requests")
    ResponseEntity<ApprovalRequest> requestApproval(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ApprovalRequestBody request,
            Authentication authentication
    ) {
        String operation = "compliance.approval.request.v1";
        AccessContext access = authorizePurpose(
                authentication, workspaceId, purpose,
                "oula.compliance.approval.request"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, purpose, request.policy().action(),
                request.policy().resourceType(), request.policy().resourceId(),
                request.policy().jurisdiction(), request.policy().amount(),
                request.policy().currency(), request.justification()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                ApprovalRequest.class,
                () -> kernel.requestApproval(
                        access, request.policy().toPolicyRequest(),
                        request.justification(), correlation
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/approval-requests/{approvalRequestId}/decision")
    ResponseEntity<ApprovalRequest> decideApproval(
            @PathVariable UUID approvalRequestId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ApprovalDecisionRequest request,
            Authentication authentication
    ) {
        String operation = "compliance.approval.decide.v1";
        AccessContext access = authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, "oula.compliance.approval.decide"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, approvalRequestId,
                request.approve(), request.note()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.OK.value(),
                ApprovalRequest.class,
                () -> kernel.decideApproval(
                        access, approvalRequestId,
                        request.approve(), request.note(), correlation
                )
        );
        return ok(result.replayed(), result.value());
    }

    private AccessContext authorizePurpose(
            Authentication authentication,
            UUID workspaceId,
            String requestedPurpose,
            String scope
    ) {
        AccessPurpose parsed;
        try {
            parsed = AccessPurpose.valueOf(requestedPurpose);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("unknown OULA purpose");
        }
        return authorizer.require(
                authentication, workspaceId, requestedPurpose, parsed, scope
        );
    }

    private <T> ResponseEntity<T> ok(boolean replayed, T value) {
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    record PolicyEvaluationRequest(
            @NotBlank @Size(max = 120) String action,
            @NotBlank @Size(max = 80) String resourceType,
            @NotNull UUID resourceId,
            @NotBlank @Size(max = 80) String jurisdiction,
            @DecimalMin("0.0") BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            boolean verificationPresent,
            boolean evidencePresent,
            UUID approvalRequestId,
            Map<String, Object> context
    ) {
        PolicyRequest toPolicyRequest() {
            return new PolicyRequest(
                    action, resourceType, resourceId, jurisdiction,
                    amount, currency, verificationPresent, evidencePresent,
                    approvalRequestId, context
            );
        }
    }

    record PolicyRuleRequest(
            @NotBlank @Size(max = 120) String policyKey,
            @NotBlank @Size(max = 40) String version,
            @NotNull AccessPurpose appliesPurpose,
            @NotBlank @Size(max = 120) String actionPattern,
            @NotBlank @Size(max = 80) String resourceTypePattern,
            @NotBlank @Size(max = 80) String jurisdictionPattern,
            @NotNull PolicyDecision.Decision ruleEffect,
            @DecimalMin("0.0") BigDecimal maxAmount,
            @Size(min = 3, max = 3) String currency,
            boolean requiresVerification,
            boolean requiresEvidence,
            @Min(-1000) @Max(1000) int priority,
            Instant effectiveFrom,
            Instant effectiveUntil
    ) {}

    record ApprovalRequestBody(
            @NotNull @Valid PolicyEvaluationRequest policy,
            @NotBlank @Size(max = 4000) String justification
    ) {}

    record ApprovalDecisionRequest(
            boolean approve,
            @NotBlank @Size(max = 4000) String note
    ) {}
}
