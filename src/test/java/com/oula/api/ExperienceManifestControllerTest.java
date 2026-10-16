package com.oula.api;

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

class ExperienceManifestControllerTest {
    private final ExperienceManifestController controller =
            new ExperienceManifestController(new WorkspacePurposeAuthorizer());

    @Test
    void displaysOnlyCapabilitiesActuallyGrantedByJwt() {
        UUID ws = UUID.randomUUID();
        var auth = token(ws, "PROPERTY_DECISION_SUPPORT",
                "oula.experience.read", "oula.intent.write");
        var result = controller.get(ws, "PROPERTY_DECISION_SUPPORT", auth);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().lens()).isEqualTo("PULSE");
        assertThat(result.getBody().capabilities())
                .extracting(ExperienceManifestController.ExperienceCapability::code)
                .containsExactly("INTENT_CREATE");
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void deniesHeaderOnlyWorkspaceAndWrongPurpose() {
        UUID ws = UUID.randomUUID();
        var valid = token(ws, "PROPERTY_MANAGEMENT", "oula.experience.read");
        assertThatThrownBy(() -> controller.get(UUID.randomUUID(), "PROPERTY_MANAGEMENT", valid))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get(ws, "TRANSACTION_EXECUTION", valid))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get(ws, "AUTONOMOUS_EXECUTION", valid))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noDiscoveryWithoutDedicatedScopeOrAuthentication() {
        UUID ws = UUID.randomUUID();
        var noScope = token(ws, "PROPERTY_DECISION_SUPPORT", "oula.intent.write");
        assertThatThrownBy(() -> controller.get(ws, "PROPERTY_DECISION_SUPPORT", noScope))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get(ws, "PROPERTY_DECISION_SUPPORT", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void propertyManagerIntakeRequiresEachExistingScopeAndNeverProjectsIntoBuyerPurpose() {
        UUID ws = UUID.randomUUID();
        var manager = token(ws, "PROPERTY_MANAGEMENT", "oula.experience.read",
                "oula.property.write", "oula.listing.read");
        var result = controller.get(ws, "PROPERTY_MANAGEMENT", manager);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().lens()).isEqualTo("HABITAT");
        assertThat(result.getBody().capabilities())
                .extracting(ExperienceManifestController.ExperienceCapability::code)
                .containsExactly("PROPERTY_INTAKE", "PRIVATE_LISTING_READ");
        assertThat(result.getBody().capabilities())
                .extracting(ExperienceManifestController.ExperienceCapability::code)
                .doesNotContain("PRIVATE_LISTING_DRAFT", "DEAL_ADVANCE");

        var buyer = token(ws, "PROPERTY_DECISION_SUPPORT", "oula.experience.read",
                "oula.property.write", "oula.listing.write", "oula.listing.read");
        var buyerResult = controller.get(ws, "PROPERTY_DECISION_SUPPORT", buyer);
        assertThat(buyerResult.getBody()).isNotNull();
        assertThat(buyerResult.getBody().capabilities()).isEmpty();
    }

    @Test
    void managersNeverReceiveTransactionCapabilitiesFromOtherPurposes() {
        UUID ws = UUID.randomUUID();
        var both = token(ws, "PROPERTY_MANAGEMENT", "oula.experience.read",
                "oula.property.management.read", "oula.transaction.advance");
        var result = controller.get(ws, "PROPERTY_MANAGEMENT", both);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().capabilities())
                .extracting(ExperienceManifestController.ExperienceCapability::code)
                .containsExactly("MANAGEMENT_OVERVIEW");
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
