package com.oula.iam;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {
    private final Environment environment;

    SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    SecurityFilterChain oulaSecurityFilterChain(HttpSecurity http) throws Exception {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean devAuth = activeProfiles.contains("dev-auth") && !activeProfiles.contains("prod");
        AuthenticationEntryPoint unauthorized = unauthorizedEntryPoint();
        AccessDeniedHandler forbidden = forbiddenHandler();

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (devAuth) {
                        authorize.requestMatchers("/dev-auth/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${oula.security.allowed-origins:}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-OULA-Workspace-ID",
                "X-OULA-Purpose",
                "Idempotency-Key"
        ));
        configuration.setExposedHeaders(List.of(
                "Idempotency-Replayed",
                "Retry-After"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> writeProblem(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "A valid OULA bearer token is required.",
                "urn:oula:problem:unauthorized"
        );
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, exception) -> writeProblem(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                "The authenticated identity is not authorized for this operation.",
                "urn:oula:problem:forbidden"
        );
    }

    private void writeProblem(
            HttpServletResponse response,
            int status,
            String title,
            String detail,
            String type
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.getWriter().write(
                "{\"type\":\"" + type
                        + "\",\"title\":\"" + title
                        + "\",\"status\":" + status
                        + ",\"detail\":\"" + detail + "\"}"
        );
    }
}
