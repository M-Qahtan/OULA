package com.oula.iam;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class WorkspacePurposeAuthorizer {

    public AccessContext require(
            Authentication authentication,
            UUID workspaceId,
            String requestedPurpose,
            AccessPurpose requiredPurpose,
            String requiredScope
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AccessDeniedException("JWT authentication is required");
        }

        if (!requiredPurpose.name().equals(requestedPurpose)) {
            throw new AccessDeniedException("request purpose is not valid for this operation");
        }

        Set<String> workspaces = claimValues(jwtAuthentication, "oula_workspace_ids");
        if (!workspaces.contains(workspaceId.toString())) {
            throw new AccessDeniedException("workspace membership is missing");
        }

        Set<String> purposes = claimValues(jwtAuthentication, "oula_purposes");
        if (!purposes.contains(requiredPurpose.name())) {
            throw new AccessDeniedException("purpose is not authorized");
        }

        String authority = "SCOPE_" + requiredScope;
        boolean scopeGranted = jwtAuthentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
        if (!scopeGranted) {
            throw new AccessDeniedException("required OAuth scope is missing");
        }

        Object actorClaim = jwtAuthentication.getToken().getClaim("actor_id");
        if (actorClaim == null) {
            throw new AccessDeniedException("actor_id claim is required");
        }

        UUID actorId;
        try {
            actorId = UUID.fromString(actorClaim.toString());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("actor_id claim is invalid");
        }

        return new AccessContext(
                actorId,
                jwtAuthentication.getToken().getSubject(),
                workspaceId,
                requiredPurpose
        );
    }

    private Set<String> claimValues(JwtAuthenticationToken authentication, String claimName) {
        Object value = authentication.getToken().getClaim(claimName);
        if (value == null) {
            return Set.of();
        }
        if (value instanceof Collection<?> collection) {
            Set<String> values = new HashSet<>();
            collection.forEach(item -> values.add(String.valueOf(item)));
            return Set.copyOf(values);
        }
        return Set.of(String.valueOf(value));
    }
}
