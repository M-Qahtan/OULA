package com.oula.api;

import com.oula.agent.CognitivePlanner;
import com.oula.iam.InstitutionalMembershipRegistry;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Negative authorization checks at the HTTP controller's exact Java seam. */
class CognitiveAgentControllerTest {
    private final CognitiveAgentController controller = new CognitiveAgentController(
            new WorkspacePurposeAuthorizer(mock(InstitutionalMembershipRegistry.class)),
            new CognitivePlanner());

    @Test
    void jwtScopesFilterProposedToolsButNeverGrantAgentExecution() {
        UUID ws = UUID.randomUUID();
        var jwt = token(ws, "PROPERTY_DECISION_SUPPORT",
                "oula.experience.read", "oula.property.passport.read");
        var response = controller.plan(ws, "PROPERTY_DECISION_SUPPORT",
                new CognitiveAgentController.PlanRequest("Find a family home"), jwt);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var value = response.getBody();
        assertThat(value).isNotNull();
        assertThat(value.workspaceId()).isEqualTo(ws);
        assertThat(value.status()).isEqualTo("PLAN_ONLY");
        assertThat(value.tools())
                .anySatisfy(tool -> {
                    assertThat(tool.code()).isEqualTo("PROPERTY_PASSPORT");
                    assertThat(tool.availability()).isEqualTo("AVAILABLE_FOR_SEPARATE_REQUEST");
                });
        assertThat(value.tools()).filteredOn(t -> t.code().equals("INTENT_READ"))
                .allSatisfy(t -> assertThat(t.availability()).isEqualTo("MISSING_SCOPE"));
    }

    @Test
    void rejectsHeaderOnlyWorkspacePurposeAndUnsupportedAutonomy() {
        UUID ws = UUID.randomUUID();
        var jwt = token(ws, "PROPERTY_DECISION_SUPPORT", "oula.experience.read");
        var request = new CognitiveAgentController.PlanRequest("Find a family home");
        assertThatThrownBy(() -> controller.plan(UUID.randomUUID(),
                "PROPERTY_DECISION_SUPPORT", request, jwt)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.plan(ws,
                "PROPERTY_MANAGEMENT", request, jwt)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.plan(ws,
                "AUTONOMOUS_EXECUTION", request, jwt)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requiresDedicatedExperienceScopeAndAuthenticatedToken() {
        UUID ws = UUID.randomUUID();
        var request = new CognitiveAgentController.PlanRequest("Find a family home");
        assertThatThrownBy(() -> controller.plan(ws, "PROPERTY_DECISION_SUPPORT", request,
                token(ws, "PROPERTY_DECISION_SUPPORT", "oula.intent.read")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.plan(ws, "PROPERTY_DECISION_SUPPORT", request, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private JwtAuthenticationToken token(UUID workspace, String purpose, String... scopes) {
        var jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("test-subject")
                .claim("actor_id", UUID.randomUUID().toString())
                .claim("oula_workspace_ids", List.of(workspace.toString()))
                .claim("oula_purposes", List.of(purpose))
                .build();
        var authorities = Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
