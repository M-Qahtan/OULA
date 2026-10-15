package com.oula.iam.dev;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dev-auth")
@Profile("dev-auth")
class DevAuthController {
    private final DevAuthFixtureCatalog fixtures;
    private final DevJwtTokenService tokens;

    DevAuthController(
            DevAuthFixtureCatalog fixtures,
            DevJwtTokenService tokens
    ) {
        this.fixtures = fixtures;
        this.tokens = tokens;
    }

    @GetMapping("/fixtures")
    List<FixtureView> fixtures() {
        return fixtures.all().stream().map(FixtureView::from).toList();
    }

    @PostMapping("/token")
    ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        DevAuthFixture fixture = fixtures.require(request.fixture());
        DevJwtTokenService.IssuedToken issued = tokens.issue(fixture);
        TokenResponse body = new TokenResponse(
                issued.accessToken(),
                "Bearer",
                issued.expiresAt(),
                "DEV_TEST_ONLY",
                fixture.alias(),
                fixture.actorId(),
                fixture.workspaceId(),
                fixture.purposes(),
                fixture.scopes()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record TokenRequest(@NotBlank String fixture) {
    }

    record TokenResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String mode,
            String fixture,
            UUID actorId,
            UUID workspaceId,
            List<String> purposes,
            List<String> scopes
    ) {
    }

    record FixtureView(
            String fixture,
            UUID workspaceId,
            String workspaceName,
            List<String> purposes,
            List<String> scopes
    ) {
        static FixtureView from(DevAuthFixture fixture) {
            return new FixtureView(
                    fixture.alias(),
                    fixture.workspaceId(),
                    fixture.workspaceName(),
                    fixture.purposes(),
                    fixture.scopes()
            );
        }
    }
}
