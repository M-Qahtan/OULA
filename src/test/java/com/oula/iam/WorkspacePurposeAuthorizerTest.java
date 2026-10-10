package com.oula.iam;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IAM regression tests that do not require a live identity provider.
 * PostgreSQL/PostGIS integration tests remain mandatory in CI.
 */
class WorkspacePurposeAuthorizerTest {
    private static final String SCOPE = "oula.property.passport.read";
    private static final AccessPurpose PURPOSE = AccessPurpose.PROPERTY_DECISION_SUPPORT;
    private final WorkspacePurposeAuthorizer authorizer = new WorkspacePurposeAuthorizer();

    @Test
    void validJwtBindsActorSubjectWorkspacePurposeAndScope() {
        UUID actor = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        JwtAuthenticationToken token = token("user-123", actor.toString(), workspace, PURPOSE.name(), SCOPE);

        AccessContext access = authorizer.require(token, workspace, PURPOSE.name(), PURPOSE, SCOPE);

        assertThat(access.actorId()).isEqualTo(actor);
        assertThat(access.subject()).isEqualTo("user-123");
        assertThat(access.workspaceId()).isEqualTo(workspace);
        assertThat(access.purpose()).isEqualTo(PURPOSE);
    }

    @Test
    void rejectsMissingOrUnauthenticatedJwt() {
        UUID workspace = UUID.randomUUID();
        assertThatThrownBy(() -> authorizer.require(null, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class);

        JwtAuthenticationToken unauthenticated = token("user-123", UUID.randomUUID().toString(),
                workspace, PURPOSE.name(), SCOPE);
        unauthenticated.setAuthenticated(false);
        assertThatThrownBy(() -> authorizer.require(unauthenticated, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsBlankSubjectEvenWhenActorIdAndScopeArePresent() {
        UUID workspace = UUID.randomUUID();
        JwtAuthenticationToken token = token("  ", UUID.randomUUID().toString(),
                workspace, PURPOSE.name(), SCOPE);

        assertThatThrownBy(() -> authorizer.require(token, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void rejectsForeignWorkspaceEvenWithValidPurposeAndScope() {
        UUID workspace = UUID.randomUUID();
        JwtAuthenticationToken token = token("user-123", UUID.randomUUID().toString(),
                UUID.randomUUID(), PURPOSE.name(), SCOPE);

        assertThatThrownBy(() -> authorizer.require(token, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspace");
    }

    @Test
    void rejectsPurposeOrScopeMismatch() {
        UUID workspace = UUID.randomUUID();
        JwtAuthenticationToken wrongPurpose = token("user-123", UUID.randomUUID().toString(),
                workspace, AccessPurpose.PROPERTY_MANAGEMENT.name(), SCOPE);
        JwtAuthenticationToken wrongScope = token("user-123", UUID.randomUUID().toString(),
                workspace, PURPOSE.name(), "oula.other.read");

        assertThatThrownBy(() -> authorizer.require(wrongPurpose, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("purpose");
        assertThatThrownBy(() -> authorizer.require(wrongScope, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void rejectsMalformedActorIdentity() {
        UUID workspace = UUID.randomUUID();
        JwtAuthenticationToken token = token("user-123", "not-a-uuid",
                workspace, PURPOSE.name(), SCOPE);

        assertThatThrownBy(() -> authorizer.require(token, workspace, PURPOSE.name(), PURPOSE, SCOPE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("actor_id");
    }

    private JwtAuthenticationToken token(String subject, String actorId, UUID workspace,
                                         String purpose, String scope) {
        Jwt jwt = Jwt.withTokenValue("unit-test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("actor_id", actorId)
                .claim("oula_workspace_ids", List.of(workspace.toString()))
                .claim("oula_purposes", List.of(purpose))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
