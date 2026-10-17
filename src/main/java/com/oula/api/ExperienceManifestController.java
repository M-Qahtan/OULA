package com.oula.api;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only UX discovery, never authorization to execute a command. */
@RestController
@RequestMapping("/v1/experience")
class ExperienceManifestController {
    private final WorkspacePurposeAuthorizer authorizer;

    ExperienceManifestController(WorkspacePurposeAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @GetMapping("/manifest")
    ResponseEntity<ExperienceManifest> get(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        AccessPurpose purpose = switch (requestedPurpose) {
            case "PROPERTY_DECISION_SUPPORT" -> AccessPurpose.PROPERTY_DECISION_SUPPORT;
            case "PROPERTY_MANAGEMENT" -> AccessPurpose.PROPERTY_MANAGEMENT;
            case "TRANSACTION_EXECUTION" -> AccessPurpose.TRANSACTION_EXECUTION;
            default -> throw new AccessDeniedException("purpose is not supported for UX discovery");
        };
        authorizer.require(authentication, workspaceId, requestedPurpose,
                purpose, "oula.experience.read");

        List<ExperienceCapability> catalog = switch (purpose) {
            case PROPERTY_DECISION_SUPPORT -> List.of(
                    new ExperienceCapability("INTENT_CREATE", "/v1/intents", "oula.intent.write", "COMMAND"),
                    new ExperienceCapability("MATCH_RUN", "/v1/intents/{intentId}/matches", "oula.matching.run", "COMMAND"),
                    new ExperienceCapability("PROPERTY_PASSPORT", "/v1/properties/{propertyId}/passport", "oula.property.passport.read", "QUERY")
            );
            case PROPERTY_MANAGEMENT -> List.of(
                    new ExperienceCapability("PROPERTY_PASSPORT", "/v1/properties/{propertyId}/passport", "oula.property.passport.read", "QUERY"),
                    new ExperienceCapability("MANAGEMENT_OVERVIEW", "/v1/properties/{propertyId}/management", "oula.property.management.read", "QUERY"),
                    new ExperienceCapability("PROPERTY_INTAKE", "/v1/properties", "oula.property.write", "COMMAND"),
                    new ExperienceCapability("PRIVATE_LISTING_DRAFT", "/v1/properties/{propertyId}/listings", "oula.listing.write", "COMMAND"),
                    new ExperienceCapability("PRIVATE_LISTING_READ", "/v1/listings", "oula.listing.read", "QUERY")
            );
            case TRANSACTION_EXECUTION -> List.of(
                    new ExperienceCapability("DEAL_READ", "/v1/transactions/{transactionId}", "oula.transaction.read", "QUERY"),
                    new ExperienceCapability("DEAL_ROOM_READ", "/v1/transactions/{transactionId}/deal-room", "oula.transaction.read", "QUERY"),
                    new ExperienceCapability("DEAL_ADVANCE", "/v1/transactions/{transactionId}/transitions", "oula.transaction.advance", "COMMAND")
            );
            default -> List.of();
        };

        List<ExperienceCapability> allowed = catalog.stream()
                .filter(capability -> authentication.getAuthorities().stream()
                        .anyMatch(authority -> ("SCOPE_" + capability.scope()).equals(authority.getAuthority())))
                .toList();

        String lens = switch (purpose) {
            case PROPERTY_DECISION_SUPPORT -> "PULSE";
            case PROPERTY_MANAGEMENT -> "HABITAT";
            case TRANSACTION_EXECUTION -> "NEXUS";
            default -> throw new AccessDeniedException("unsupported experience purpose");
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ExperienceManifest(workspaceId, purpose.name(), lens,
                        allowed, "AUTHENTICATED_SCOPE_PROJECTION"));
    }

    record ExperienceCapability(String code, String route, String scope, String mode) {}
    record ExperienceManifest(UUID workspaceId, String purpose, String lens,
                              List<ExperienceCapability> capabilities, String source) {}
}
