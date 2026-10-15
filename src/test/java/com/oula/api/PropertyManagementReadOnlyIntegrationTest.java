package com.oula.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unlike transactional fixture tests, this test exercises an actual read-only service transaction. */
@SpringBootTest
@AutoConfigureMockMvc
class PropertyManagementReadOnlyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void enrolledPropertyOverviewDoesNotLockRowsInsideReadOnlyTransaction() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Read-only regression test", "ACTIVE");
        jdbc.update("""
                insert into property.asset(id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """, propertyId, workspaceId, "RESIDENTIAL", "Al Yasmin", 3, 1000000);

        mvc.perform(post("/v1/properties/{propertyId}/management/enroll", propertyId)
                        .with(jwt().jwt(token -> token
                                .subject("actor-" + actorId)
                                .claim("actor_id", actorId.toString())
                                .claim("oula_workspace_ids", List.of(workspaceId.toString()))
                                .claim("oula_purposes", List.of("PROPERTY_MANAGEMENT")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_oula.property.management.write")))
                        .header("X-OULA-Workspace-ID", workspaceId)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT")
                        .header("Idempotency-Key", "enroll-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/v1/properties/{propertyId}/management", propertyId)
                        .with(jwt().jwt(token -> token
                                .subject("actor-" + actorId)
                                .claim("actor_id", actorId.toString())
                                .claim("oula_workspace_ids", List.of(workspaceId.toString()))
                                .claim("oula_purposes", List.of("PROPERTY_MANAGEMENT")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_oula.property.management.read")))
                        .header("X-OULA-Workspace-ID", workspaceId)
                        .header("X-OULA-Purpose", "PROPERTY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.obligations.length()").value(0));
    }
}
