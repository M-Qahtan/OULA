package com.oula.api;

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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.property.workorder.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, actionId, request.category(), request.title(),
                request.scopeDescription(), request.estimatedCost(), request.currency()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                WorkOrder.class,
                () -> execution.createFromAction(
                        access, actionId,
                        new CreateWorkOrderCommand(
                                request.category(), request.title(), request.scopeDescription(),
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
            @Valid @RequestBody ApproveWorkOrderRequest request,
            Authentication authentication
    ) {
        return mutate(authentication, workspaceId, purpose, key,
                "operations.work_order.approve.v1", "oula.property.workorder.approve",
                workOrderId, request.approvedBudget(),
                () -> execution.approve(
                        authorize(authentication, workspaceId, purpose,
                                "oula.property.workorder.approve"),
                        workOrderId, request.approvedBudget(),
                        DeterministicUuid.from("operations.work_order.approve.v1", workspaceId, key)
                ));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/assign")
    ResponseEntity<WorkOrder> assign(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AssignWorkOrderRequest request,
            Authentication authentication
    ) {
        return mutate(authentication, workspaceId, purpose, key,
                "operations.work_order.assign.v1", "oula.property.workorder.execute",
                workOrderId, request.providerPartyId(),
                () -> execution.assign(
                        authorize(authentication, workspaceId, purpose,
                                "oula.property.workorder.execute"),
                        workOrderId, request.providerPartyId(),
                        DeterministicUuid.from("operations.work_order.assign.v1", workspaceId, key)
                ));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/start")
    ResponseEntity<WorkOrder> start(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication authentication
    ) {
        return mutate(authentication, workspaceId, purpose, key,
                "operations.work_order.start.v1", "oula.property.workorder.execute",
                workOrderId, "start",
                () -> execution.start(
                        authorize(authentication, workspaceId, purpose,
                                "oula.property.workorder.execute"),
                        workOrderId,
                        DeterministicUuid.from("operations.work_order.start.v1", workspaceId, key)
                ));
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
        return mutate(authentication, workspaceId, purpose, key,
                "operations.work_order.completion.submit.v1", "oula.property.workorder.execute",
                workOrderId,
                List.of(request.actualCost(), request.completionEvidenceId(), request.completionNote()),
                () -> execution.submitCompletion(
                        authorize(authentication, workspaceId, purpose,
                                "oula.property.workorder.execute"),
                        workOrderId,
                        new SubmitWorkOrderCompletionCommand(
                                request.actualCost(), request.completionEvidenceId(),
                                request.completionNote()
                        ),
                        DeterministicUuid.from("operations.work_order.completion.submit.v1",
                                workspaceId, key)
                ));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/verify")
    ResponseEntity<WorkOrder> verify(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication authentication
    ) {
        return mutate(authentication, workspaceId, purpose, key,
                "operations.work_order.verify.v1", "oula.property.workorder.verify",
                workOrderId, "verify",
                () -> execution.verifyCompletion(
                        authorize(authentication, workspaceId, purpose,
                                "oula.property.workorder.verify"),
                        workOrderId,
                        DeterministicUuid.from("operations.work_order.verify.v1", workspaceId, key)
                ));
    }

    @GetMapping("/v1/properties/{propertyId}/work-orders")
    List<WorkOrder> list(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.property.workorder.read");
        return execution.list(access, propertyId);
    }

    private ResponseEntity<WorkOrder> mutate(
            Authentication authentication,
            UUID workspaceId,
            String purpose,
            String key,
            String operation,
            String scope,
            UUID workOrderId,
            Object requestState,
            java.util.function.Supplier<WorkOrder> supplier
    ) {
        authorize(authentication, workspaceId, purpose, scope);
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, workOrderId, requestState
        );
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.OK.value(),
                WorkOrder.class, supplier
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private AccessContext authorize(
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
