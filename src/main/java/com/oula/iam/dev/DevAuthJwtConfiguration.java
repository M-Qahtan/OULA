package com.oula.iam.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@Profile("dev-auth")
class DevAuthJwtConfiguration {

    @Bean
    JwtDecoder devAuthJwtDecoder(
            DevJwtKeyMaterial keys,
            DevJwtTokenService tokens
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();

        OAuth2TokenValidator<Jwt> issuer =
                JwtValidators.createDefaultWithIssuer(tokens.issuer());
        OAuth2TokenValidator<Jwt> audience = jwt ->
                jwt.getAudience().contains(tokens.audience())
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(
                                        "invalid_token",
                                        "required OULA API audience is missing",
                                        null
                                )
                        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }
}
