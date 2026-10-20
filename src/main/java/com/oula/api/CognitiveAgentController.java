package com.oula.api;

import com.oula.agent.CognitivePlan;
import com.oula.agent.CognitivePlanner;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticated advisory-only plan creation. POST is computation, not a domain
 * write. Tool execution, personal memory and model routing are NOT enabled.
 */
@RestController
@RequestMapping("/v1/agent")
class CognitiveAgentController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final CognitivePlanner planner;

    CognitiveAgentController(WorkspacePurposeAuthorizer authorizer, CognitivePlanner planner) {
        this.authorizer = authorizer;
        this.planner = planner;
    }

    @PostMapping("/plans")
    ResponseEntity<CognitivePlan> plan(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @Valid @RequestBody PlanRequest request,
            Authentication authentication
    ) {
        AccessPurpose purpose = switch (requestedPurpose) {
            case "PROPERTY_DECISION_SUPPORT" -> AccessPurpose.PROPERTY_DECISION_SUPPORT;
            case "PROPERTY_MANAGEMENT" -> AccessPurpose.PROPERTY_MANAGEMENT;
            case "TRANSACTION_EXECUTION" -> AccessPurpose.TRANSACTION_EXECUTION;
            default -> throw new AccessDeniedException("unsupported agent planning purpose");
        };
        AccessContext access = authorizer.require(authentication, workspaceId, requestedPurpose,
                purpose, "oula.experience.read");
        Set<String> scopes = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value.startsWith("SCOPE_"))
                .map(value -> value.substring("SCOPE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(planner.plan(access, scopes, request.goal()));
    }

    record PlanRequest(@NotBlank @Size(max = 512) String goal) {}
}
