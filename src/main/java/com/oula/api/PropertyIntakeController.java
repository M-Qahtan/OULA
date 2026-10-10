package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.property.PropertyIntakeService;
import com.oula.property.PropertyIntakeService.ListingView;
import com.oula.property.PropertyIntakeService.PropertyView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Private workspace-scoped declared inventory. Never publishes or verifies listings. */
@RestController
@RequestMapping("/v1")
class PropertyIntakeController {
    private static final String PROPERTY_OP = "property.intake.v1";
    private static final String LISTING_OP = "listing.intake.v1";
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PropertyIntakeService intake;

    PropertyIntakeController(WorkspacePurposeAuthorizer authorizer,
                             IdempotencyService idempotency, PropertyIntakeService intake) {
        this.authorizer=authorizer;
        this.idempotency=idempotency;
        this.intake=intake;
    }

    @PostMapping("/properties")
    ResponseEntity<PropertyView> createProperty(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspace,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PropertyCreateRequest request,
            Authentication authentication) {
        AccessContext access=authorize(authentication,workspace,purpose,"oula.property.write");
        String hash=RequestFingerprint.sha256(PROPERTY_OP,workspace,request.assetType(),
                request.district(),request.bedrooms(),request.askingPrice(),request.demo());
        UUID id=DeterministicUuid.from(PROPERTY_OP,workspace,key);
        var result=idempotency.execute(workspace,key,PROPERTY_OP,hash,201,PropertyView.class,
                ()->intake.createProperty(access,id,request.assetType(),request.district(),
                        request.bedrooms(),request.askingPrice(),request.demo()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @GetMapping("/properties/{propertyId}")
    ResponseEntity<PropertyView> getProperty(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspace,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(intake.getProperty(authorize(authentication,workspace,purpose,
                        "oula.property.read"),propertyId));
    }

    @PostMapping("/properties/{propertyId}/listings")
    ResponseEntity<ListingView> createListing(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspace,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ListingCreateRequest request,
            Authentication authentication) {
        AccessContext access=authorize(authentication,workspace,purpose,"oula.listing.write");
        String hash=RequestFingerprint.sha256(LISTING_OP,workspace,propertyId,
                request.transactionType(),request.askingPrice());
        UUID id=DeterministicUuid.from(LISTING_OP,workspace,key);
        var result=idempotency.execute(workspace,key,LISTING_OP,hash,201,ListingView.class,
                ()->intake.createListing(access,id,propertyId,request.transactionType(),
                        request.askingPrice()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(result.value());
    }

    @GetMapping("/listings/{listingId}")
    ResponseEntity<ListingView> getListing(
            @PathVariable UUID listingId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspace,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(intake.getListing(authorize(authentication,workspace,purpose,
                        "oula.listing.read"),listingId));
    }

    @GetMapping("/listings")
    ResponseEntity<List<ListingView>> searchListings(
            @RequestParam(required=false) String district,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspace,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(intake.searchListings(authorize(authentication,workspace,purpose,
                        "oula.listing.read"),district));
    }

    private AccessContext authorize(Authentication auth,UUID workspace,String purpose,String scope) {
        return authorizer.require(auth,workspace,purpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,scope);
    }

    record PropertyCreateRequest(
            @NotNull @Pattern(regexp="RESIDENTIAL|COMMERCIAL") String assetType,
            @NotBlank @Size(max=160) String district,
            @NotNull @Min(0) @Max(100) Integer bedrooms,
            @NotNull @DecimalMin("0.01") BigDecimal askingPrice,
            boolean demo) {}

    record ListingCreateRequest(
            @NotNull @Pattern(regexp="SALE|RENT") String transactionType,
            @NotNull @DecimalMin("0.01") BigDecimal askingPrice) {}
}
