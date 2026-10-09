package com.oula.api;

import com.oula.advisory.RentalLifecycleAdvisory;
import com.oula.advisory.RentalLifecycleAdvisoryService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
class RentalLifecycleAdvisoryController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final RentalLifecycleAdvisoryService rentalAdvice;

    RentalLifecycleAdvisoryController(WorkspacePurposeAuthorizer authorizer,
                                     RentalLifecycleAdvisoryService rentalAdvice) {
        this.authorizer = authorizer;
        this.rentalAdvice = rentalAdvice;
    }

    @GetMapping("/v1/properties/{propertyId}/rental-lifecycle-advice")
    ResponseEntity<RentalLifecycleAdvisory> advise(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        AccessContext access = authorizer.require(authentication, workspaceId, purpose,
                AccessPurpose.PROPERTY_MANAGEMENT, "oula.property.rental-advisory.read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(rentalAdvice.recommend(access, propertyId));
    }
}
