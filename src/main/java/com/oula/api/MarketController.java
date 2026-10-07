package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.market.ListingView;
import com.oula.market.MarketApplicationService;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/v1/listings")
class MarketController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final MarketApplicationService market;

    MarketController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            MarketApplicationService market
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.market = market;
    }

    @PostMapping
    ResponseEntity<ListingView> create(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ListingRequest request,
            Authentication authentication
    ) {
        String operation = "market.listing.create.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.listing.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.propertyId(),
                request.transactionType(), request.askingPrice(), request.currency()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), ListingView.class,
                () -> market.createDraft(
                        access,
                        request.propertyId(),
                        request.transactionType(),
                        request.askingPrice(),
                        request.currency(),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/{listingId}/publish")
    ResponseEntity<ListingView> publish(
            @PathVariable UUID listingId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionRequest request,
            Authentication authentication
    ) {
        return transition(
                "market.listing.publish.v1", "oula.listing.publish",
                listingId, workspaceId, requestedPurpose, idempotencyKey,
                request.expectedVersion(), authentication, true
        );
    }

    @PostMapping("/{listingId}/withdraw")
    ResponseEntity<ListingView> withdraw(
            @PathVariable UUID listingId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionRequest request,
            Authentication authentication
    ) {
        return transition(
                "market.listing.withdraw.v1", "oula.listing.withdraw",
                listingId, workspaceId, requestedPurpose, idempotencyKey,
                request.expectedVersion(), authentication, false
        );
    }

    @GetMapping("/{listingId}")
    ListingView get(
            @PathVariable UUID listingId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return market.get(
                authorize(authentication, workspaceId, requestedPurpose, "oula.listing.read"),
                listingId
        );
    }

    private ResponseEntity<ListingView> transition(
            String operation,
            String scope,
            UUID listingId,
            UUID workspaceId,
            String requestedPurpose,
            String idempotencyKey,
            long expectedVersion,
            Authentication authentication,
            boolean publish
    ) {
        AccessContext access = authorize(authentication, workspaceId, requestedPurpose, scope);
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, listingId, expectedVersion
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.OK.value(), ListingView.class,
                () -> publish
                        ? market.publish(access, listingId, expectedVersion, correlationId)
                        : market.withdraw(access, listingId, expectedVersion, correlationId)
        );
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    private AccessContext authorize(Authentication authentication, UUID workspaceId, String purpose, String scope) {
        return authorizer.require(
                authentication, workspaceId, purpose,
                AccessPurpose.MARKETPLACE_SUPPLY, scope
        );
    }

    record ListingRequest(
            @NotNull UUID propertyId,
            @NotBlank String transactionType,
            @NotNull @DecimalMin("0.01") BigDecimal askingPrice,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    record VersionRequest(@Min(0) long expectedVersion) {}
}
