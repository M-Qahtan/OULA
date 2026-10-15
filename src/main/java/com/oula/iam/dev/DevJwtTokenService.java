package com.oula.iam.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("dev-auth & !prod")
class DevJwtTokenService {
    private final DevJwtKeyMaterial keys;
    private final JsonMapper json;
    private final Clock clock = Clock.systemUTC();
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    DevJwtTokenService(
            DevJwtKeyMaterial keys,
            JsonMapper json,
            @Value("${oula.dev-auth.issuer:http://localhost:8080/dev-auth}") String issuer,
            @Value("${oula.dev-auth.audience:oula-api}") String audience,
            @Value("${oula.dev-auth.ttl-seconds:1800}") long ttlSeconds
    ) {
        this.keys = keys;
        this.json = json;
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
    }

    IssuedToken issue(DevAuthFixture fixture) {
        Instant now = clock.instant();
        Instant expires = now.plusSeconds(ttlSeconds);

        Map<String, Object> header = Map.of(
                "alg", "RS256",
                "typ", "JWT",
                "kid", "oula-dev-ephemeral"
        );

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", "dev-fixture:" + fixture.alias());
        claims.put("aud", List.of(audience));
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expires.getEpochSecond());
        claims.put("actor_id", fixture.actorId().toString());
        claims.put("oula_workspace_ids", List.of(fixture.workspaceId().toString()));
        claims.put("oula_purposes", fixture.purposes());
        claims.put("scope", String.join(" ", fixture.scopes()));

        String encodedHeader = base64Url(write(header));
        String encodedClaims = base64Url(write(claims));
        String signingInput = encodedHeader + "." + encodedClaims;

        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String encodedSignature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(signature.sign());
            return new IssuedToken(signingInput + "." + encodedSignature, expires);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to issue dev-auth token", ex);
        }
    }

    String issuer() {
        return issuer;
    }

    String audience() {
        return audience;
    }

    private byte[] write(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to encode dev-auth token", ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record IssuedToken(String accessToken, Instant expiresAt) {
    }
}
