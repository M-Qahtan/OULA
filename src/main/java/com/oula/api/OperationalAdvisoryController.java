package com.oula.api;

import com.oula.advisory.OperationalAdvisory;
import com.oula.advisory.OperationalAdvisoryService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
class OperationalAdvisoryController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final OperationalAdvisoryService advisory;

    OperationalAdvisoryController(
            WorkspacePurposeAuthorizer authorizer,
            OperationalAdvisoryService advisory
    ) {
        this.authorizer = authorizer;
        this.advisory = advisory;
    }

    @GetMapping("/v1/properties/{propertyId}/operational-advice")
    ResponseEntity<OperationalAdvisory> recommend(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessContext access = authorizer.require(
                authentication, workspaceId, requestedPurpose,
                AccessPurpose.PROPERTY_MANAGEMENT, "oula.property.advisory.read"
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(advisory.recommend(access, propertyId));
    }
}
