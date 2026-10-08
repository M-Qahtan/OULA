package com.oula.api;

import com.oula.compliance.PolicyEnforcementContext;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.operations.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
class PropertyOperationsController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final OperationsExecutionService execution;

    PropertyOperationsController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            OperationsExecutionService execution
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.execution = execution;
    }

    @PostMapping("/v1/property-actions/{actionId}/work-orders")
    ResponseEntity<WorkOrder> create(
            @PathVariable UUID actionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateWorkOrderRequest request,
            Authentication authentication
    ) {
        String operation = "operations.work_order.create.v1";
        AccessContext access = authorizeHuman(
                authentication, workspaceId, purpose,
                "oula.property.workorder.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, actionId, request.category(),
                request.title(), request.scopeDescription(),
                request.estimatedCost(), request.currency()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.CREATED.value(), WorkOrder.class,
                () -> execution.createFromAction(
                        access, actionId,
                        new CreateWorkOrderCommand(
                                request.category(), request.title(),
                                request.scopeDescription(),
                                request.estimatedCost(), request.currency()
                        ),
                        correlation
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/v1/work-orders/{workOrderId}/approve")
    ResponseEntity<WorkOrder> approve(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-OULA-Jurisdiction", defaultValue = "UNSPECIFIED")
            String jurisdiction,
            @RequestHeader(value = "X-OULA-Approval-ID", required = false)
            UUID approvalId,
            @Valid @RequestBody ApproveWorkOrderRequest request,
            Authentication authentication
    ) {
        String operation = "operations.work_order.approve.v1";
        AccessContext access = authorizeOperational(
                authentication, workspaceId, purpose,
                "oula.property.workorder.approve",
                "oula.autonomous.workorder.approve"
        );
        PolicyEnforcementContext policy = policy(
                jurisdiction, approvalId, operation
        );
        return mutate(
                workspaceId, key, operation, workOrderId,
                List.of(request.approvedBudget(), jurisdiction, approvalId == null ? "" : approvalId),
                () -> execution.approve(
                        access, workOrderId, request.approvedBudget(),
                        policy,
                        DeterministicUuid.from(operation, workspaceId, key)
                )
        );
    }

    @PostMapping("/v1/work-orders/{workOrderId}/assign")
    ResponseEntity<WorkOrder> assign(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-OULA-Jurisdiction", defaultValue = "UNSPECIFIED")
            String jurisdiction,
            @RequestHeader(value = "X-OULA-Approval-ID", required = false)
            UUID approvalId,
            @Valid @RequestBody AssignWorkOrderRequest request,
            Authentication authentication
    ) {
        String operation = "operations.work_order.assign.v1";
        AccessContext access = authorizeOperational(
                authentication, workspaceId, purpose,
                "oula.property.workorder.execute",
                "oula.autonomous.workorder.assign"
        );
        PolicyEnforcementContext policy = policy(
                jurisdiction, approvalId, operation
        );
        return mutate(
                workspaceId, key, operation, workOrderId,
                List.of(request.providerPartyId(), jurisdiction, approvalId == null ? "" : approvalId),
                () -> execution.assign(
                        access, workOrderId, request.providerPartyId(),
                        policy,
                        DeterministicUuid.from(operation, workspaceId, key)
                )
        );
    }

    @PostMapping("/v1/work-orders/{workOrderId}/start")
    ResponseEntity<WorkOrder> start(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-OULA-Jurisdiction", defaultValue = "UNSPECIFIED")
            String jurisdiction,
            @RequestHeader(value = "X-OULA-Approval-ID", required = false)
            UUID approvalId,
            Authentication authentication
    ) {
        String operation = "operations.work_order.start.v1";
        AccessContext access = authorizeOperational(
                authentication, workspaceId, purpose,
                "oula.property.workorder.execute",
                "oula.autonomous.workorder.start"
        );
        PolicyEnforcementContext policy = policy(
                jurisdiction, approvalId, operation
        );
        return mutate(
                workspaceId, key, operation, workOrderId,
                List.of("start", jurisdiction, approvalId == null ? "" : approvalId),
                () -> execution.start(
                        access, workOrderId, policy,
                        DeterministicUuid.from(operation, workspaceId, key)
                )
        );
    }

    @PostMapping("/v1/work-orders/{workOrderId}/completion")
    ResponseEntity<WorkOrder> submitCompletion(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody SubmitCompletionRequest request,
            Authentication authentication
    ) {
        String operation = "operations.work_order.completion.submit.v1";
        AccessContext access = authorizeHuman(
                authentication, workspaceId, purpose,
                "oula.property.workorder.execute"
        );
        return mutate(
                workspaceId, key, operation, workOrderId,
                List.of(
                        request.actualCost(),
                        request.completionEvidenceId(),
                        request.completionNote()
                ),
                () -> execution.submitCompletion(
                        access, workOrderId,
                        new SubmitWorkOrderCompletionCommand(
                                request.actualCost(),
                                request.completionEvidenceId(),
                                request.completionNote()
                        ),
                        DeterministicUuid.from(operation, workspaceId, key)
                )
        );
    }

    @PostMapping("/v1/work-orders/{workOrderId}/verify")
    ResponseEntity<WorkOrder> verify(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication authentication
    ) {
        String operation = "operations.work_order.verify.v1";
        AccessContext access = authorizeHuman(
                authentication, workspaceId, purpose,
                "oula.property.workorder.verify"
        );
        return mutate(
                workspaceId, key, operation, workOrderId, "verify",
                () -> execution.verifyCompletion(
                        access, workOrderId,
                        DeterministicUuid.from(operation, workspaceId, key)
                )
        );
    }

    @GetMapping("/v1/properties/{propertyId}/work-orders")
    List<WorkOrder> list(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        AccessContext access = authorizeHuman(
                authentication, workspaceId, purpose,
                "oula.property.workorder.read"
        );
        return execution.list(access, propertyId);
    }

    private ResponseEntity<WorkOrder> mutate(
            UUID workspaceId,
            String key,
            String operation,
            UUID workOrderId,
            Object requestState,
            Supplier<WorkOrder> supplier
    ) {
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, workOrderId, requestState
        );
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint,
                HttpStatus.OK.value(), WorkOrder.class, supplier
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private AccessContext authorizeHuman(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String scope
    ) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, scope
        );
    }

    private AccessContext authorizeOperational(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String humanScope,
            String autonomousScope
    ) {
        AccessPurpose parsed;
        try {
            parsed = AccessPurpose.valueOf(purpose);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("unknown OULA purpose");
        }
        if (parsed == AccessPurpose.PROPERTY_MANAGEMENT) {
            return authorizer.require(
                    authentication, workspaceId, purpose,
                    AccessPurpose.PROPERTY_MANAGEMENT, humanScope
            );
        }
        if (parsed == AccessPurpose.AUTONOMOUS_EXECUTION) {
            return authorizer.require(
                    authentication, workspaceId, purpose,
                    AccessPurpose.AUTONOMOUS_EXECUTION, autonomousScope
            );
        }
        throw new AccessDeniedException(
                "operation requires PROPERTY_MANAGEMENT or AUTONOMOUS_EXECUTION"
        );
    }

    private PolicyEnforcementContext policy(
            String jurisdiction,
            UUID approvalId,
            String operation
    ) {
        return new PolicyEnforcementContext(
                jurisdiction, approvalId,
                Map.of("apiOperation", operation)
        );
    }

    record CreateWorkOrderRequest(
            @NotBlank @Size(max = 80) String category,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 4000) String scopeDescription,
            @NotNull @DecimalMin("0.0") BigDecimal estimatedCost,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    record ApproveWorkOrderRequest(
            @NotNull @DecimalMin("0.0") BigDecimal approvedBudget
    ) {}

    record AssignWorkOrderRequest(
            @NotNull UUID providerPartyId
    ) {}

    record SubmitCompletionRequest(
            @NotNull @DecimalMin("0.0") BigDecimal actualCost,
            @NotNull UUID completionEvidenceId,
            @NotBlank @Size(max = 4000) String completionNote
    ) {}
}
