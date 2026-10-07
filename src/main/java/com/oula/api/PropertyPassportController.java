package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.property.PropertyPassport;
import com.oula.property.PropertyPassportService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/properties/{propertyId}/passport")
class PropertyPassportController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final PropertyPassportService passports;

    PropertyPassportController(
            WorkspacePurposeAuthorizer authorizer,
            PropertyPassportService passports
    ) {
        this.authorizer = authorizer;
        this.passports = passports;
    }

    @GetMapping
    PropertyPassport get(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessPurpose purpose = switch (requestedPurpose) {
            case "PROPERTY_DECISION_SUPPORT" -> AccessPurpose.PROPERTY_DECISION_SUPPORT;
            case "PROPERTY_MANAGEMENT" -> AccessPurpose.PROPERTY_MANAGEMENT;
            default -> throw new AccessDeniedException("purpose is not valid for Property Passport");
        };
        AccessContext access = authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                purpose,
                "oula.property.passport.read"
        );
        return passports.get(access, propertyId);
    }
}
