package com.oula.iam;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.UUID;

/**
 * Durable institutional access gate. Workspace IDs and scopes in an otherwise
 * valid JWT alone are insufficient for office or enterprise data access.
 *
 * Production memberships must be provisioned through a separately verified
 * identity/organization onboarding process; this component never self-enrols.
 */
@Component
public class InstitutionalMembershipRegistry {
    private final JdbcClient jdbc;

    public InstitutionalMembershipRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A personal workspace retains the historical signed-JWT access contract.
     * Every nonpersonal workspace fails closed without issuer/subject-bound
     * institutional membership. Missing or disabled workspaces fail closed.
     */
    public void requireAuthorizedWorkspace(
            UUID workspaceId, UUID actorId, String subject, URL issuer) {
        var workspace = jdbc.sql("""
                select workspace_type, status from iam.workspace where id=:workspace
                """).param("workspace", workspaceId)
                .query((rs,n)->new WorkspaceState(rs.getString("workspace_type"),rs.getString("status")))
                .optional().orElseThrow(()->new AccessDeniedException("workspace is not registered"));
        if (!"ACTIVE".equals(workspace.status())) {
            throw new AccessDeniedException("workspace is not active");
        }
        if ("PERSONAL".equals(workspace.type())) {
            return;
        }
        requireActiveMember(workspaceId, actorId, subject, issuer);
    }

    public void requireActiveMember(
            UUID workspaceId, UUID actorId, String subject, URL issuer) {
        if (issuer == null || issuer.toString().isBlank() || subject == null || subject.isBlank()) {
            throw new AccessDeniedException("institutional membership needs a signed issuer and subject");
        }
        Long count = jdbc.sql("""
                select count(*)
                  from iam.institutional_membership
                 where workspace_id=:workspace and actor_id=:actor
                   and issuer=:issuer and subject=:subject and status='ACTIVE'
                """)
                .param("workspace",workspaceId).param("actor",actorId)
                .param("issuer",issuer.toString()).param("subject",subject)
                .query(Long.class).single();
        if (count == null || count != 1L) {
            throw new AccessDeniedException("active issuer-bound institutional membership required");
        }
    }

    /**
     * Before issuing a private deal invitation, require a provisioned active
     * recipient in the office's durable roster. Recipient still must accept
     * using its OWN issuer/subject-bound JWT before reading anything.
     */
    public void requireEligibleDealRecipient(UUID workspaceId, UUID targetActorId) {
        var workspace = jdbc.sql("""
                select workspace_type, status from iam.workspace where id=:workspace
                """).param("workspace",workspaceId)
                .query((rs,n)->new WorkspaceState(rs.getString("workspace_type"),rs.getString("status")))
                .optional().orElseThrow(()->new AccessDeniedException("workspace not registered"));
        if (!"ACTIVE".equals(workspace.status())) {
            throw new AccessDeniedException("workspace inactive");
        }
        if ("PERSONAL".equals(workspace.type())) {
            return;
        }
        Long count = jdbc.sql("""
                select count(*) from iam.institutional_membership
                 where workspace_id=:workspace and actor_id=:target and status='ACTIVE'
                """)
                .param("workspace",workspaceId).param("target",targetActorId)
                .query(Long.class).single();
        if (count == null || count != 1L) {
            throw new AccessDeniedException("invited actor is not an active member of this institution");
        }
    }

    private record WorkspaceState(String type, String status) {}
}
