package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Independent QA regression: reject requests without an authorized workspace and purpose. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntentAuthorizationRegressionTest {
    @Autowired MockMvc mvc;

    @Test
    void missingAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                .header("X-OULA-Workspace-ID", UUID.randomUUID())
                .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongWorkspaceClaimIsForbidden() throws Exception {
        UUID requestedWorkspace = UUID.randomUUID();
        UUID tokenWorkspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        mvc.perform(get("/v1/intents/{id}", UUID.randomUUID())
                .with(jwt().jwt(j -> j
                        .subject("qa-user")
                        .claim("actor_id", actor.toString())
                        .claim("oula_workspace_ids", List.of(tokenWorkspace.toString()))
                        .claim("oula_purposes", List.of("PROPERTY_DECISION_SUPPORT")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_oula.intent.read")))
                .header("X-OULA-Workspace-ID", requestedWorkspace)
                .header("X-OULA-Purpose", "PROPERTY_DECISION_SUPPORT"))
                .andExpect(status().isForbidden());
    }
}
