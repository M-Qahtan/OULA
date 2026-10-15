package com.oula.iam.dev;

import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dev-auth/golden-path")
@Profile("dev-auth & !prod")
class DevGoldenPathFixtureController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final DevGoldenPathFixtureService fixtures;

    DevGoldenPathFixtureController(
            WorkspacePurposeAuthorizer authorizer,
            DevGoldenPathFixtureService fixtures
    ) {
        this.authorizer = authorizer;
        this.fixtures = fixtures;
    }

    @PostMapping("/intents/{intentId}/prepare")
    DevGoldenPathFixtureService.PreparedFixture prepareIntent(
            @PathVariable UUID intentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                "oula.matching.run"
        );
        return fixtures.prepareIntent(intentId, workspaceId);
    }
}
