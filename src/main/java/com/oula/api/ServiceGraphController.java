package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.services.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
class ServiceGraphController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final ServiceGraphService services;

    ServiceGraphController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            ServiceGraphService services
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.services = services;
    }

    @PostMapping("/v1/service-providers")
    ResponseEntity<ServiceProvider> registerProvider(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RegisterProviderRequest request,
            Authentication authentication
    ) {
        String operation = "service_graph.provider.register.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.provider.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.providerPartyId(), request.displayName()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                ServiceProvider.class,
                () -> services.registerProvider(
                        access, request.providerPartyId(), request.displayName(), correlation
                )
        );
        return created(result.replayed(), result.value());
    }

    @PostMapping("/v1/service-providers/{providerId}/verify")
    ResponseEntity<ServiceProvider> verifyProvider(
            @PathVariable UUID providerId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody VerifyProviderRequest request,
            Authentication authentication
    ) {
        String operation = "service_graph.provider.verify.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.provider.verify");
        return ok(workspaceId, key, operation,
                RequestFingerprint.sha256(operation, workspaceId, providerId, request.evidenceId()),
                ServiceProvider.class,
                () -> services.verifyProvider(
                        access, providerId, request.evidenceId(),
                        DeterministicUuid.from(operation, workspaceId, key)
                ));
    }

    @PostMapping("/v1/service-providers/{providerId}/capabilities")
    ResponseEntity<ProviderCapability> addCapability(
            @PathVariable UUID providerId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CapabilityRequest request,
            Authentication authentication
    ) {
        String operation = "service_graph.provider.capability.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.provider.write");
        return ok(workspaceId, key, operation,
                RequestFingerprint.sha256(operation, workspaceId, providerId, request.category()),
                ProviderCapability.class,
                () -> services.addCapability(
                        access, providerId, request.category(),
                        DeterministicUuid.from(operation, workspaceId, key)
                ));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/quotes")
    ResponseEntity<ServiceQuote> submitQuote(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody QuoteRequest request,
            Authentication authentication
    ) {
        String operation = "service_graph.quote.submit.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.quote.write");
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, workOrderId, request.providerId(),
                request.amount(), request.currency(), request.leadTimeDays(),
                request.scopeNote()
        );
        UUID correlation = DeterministicUuid.from(operation, workspaceId, key);
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.CREATED.value(),
                ServiceQuote.class,
                () -> services.submitQuote(
                        access, workOrderId, request.providerId(), request.amount(),
                        request.currency(), request.leadTimeDays(), request.scopeNote(),
                        correlation
                )
        );
        return created(result.replayed(), result.value());
    }

    @GetMapping("/v1/work-orders/{workOrderId}/quotes")
    List<ServiceQuote> listQuotes(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication
    ) {
        return services.listQuotes(
                authorize(authentication, workspaceId, purpose, "oula.services.quote.read"),
                workOrderId
        );
    }

    @PostMapping("/v1/service-quotes/{quoteId}/select")
    ResponseEntity<ServiceQuote> selectQuote(
            @PathVariable UUID quoteId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication authentication
    ) {
        String operation = "service_graph.quote.select.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.quote.select");
        return ok(workspaceId, key, operation,
                RequestFingerprint.sha256(operation, workspaceId, quoteId),
                ServiceQuote.class,
                () -> services.selectQuote(
                        access, quoteId,
                        DeterministicUuid.from(operation, workspaceId, key)
                ));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/provider-outcome")
    ResponseEntity<ProviderOutcome> recordOutcome(
            @PathVariable UUID workOrderId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ProviderOutcomeRequest request,
            Authentication authentication
    ) {
        String operation = "service_graph.provider_outcome.record.v1";
        AccessContext access = authorize(authentication, workspaceId, purpose,
                "oula.services.outcome.write");
        return ok(workspaceId, key, operation,
                RequestFingerprint.sha256(
                        operation, workspaceId, workOrderId,
                        request.rating(), request.outcomeNote()
                ),
                ProviderOutcome.class,
                () -> services.recordOutcome(
                        access, workOrderId, request.rating(), request.outcomeNote(),
                        DeterministicUuid.from(operation, workspaceId, key)
                ));
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

    private <T> ResponseEntity<T> created(boolean replayed, T value) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    private <T> ResponseEntity<T> ok(
            UUID workspaceId,
            String key,
            String operation,
            String fingerprint,
            Class<T> type,
            Supplier<T> supplier
    ) {
        var result = idempotency.execute(
                workspaceId, key, operation, fingerprint, HttpStatus.OK.value(),
                type, supplier
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    record RegisterProviderRequest(
            @NotNull UUID providerPartyId,
            @NotBlank @Size(max = 255) String displayName
    ) {}

    record VerifyProviderRequest(@NotNull UUID evidenceId) {}

    record CapabilityRequest(
            @NotBlank @Size(max = 80) String category
    ) {}

    record QuoteRequest(
            @NotNull UUID providerId,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Min(0) int leadTimeDays,
            @NotBlank @Size(max = 4000) String scopeNote
    ) {}

    record ProviderOutcomeRequest(
            @Min(1) @Max(5) Integer rating,
            @Size(max = 4000) String outcomeNote
    ) {}
}
