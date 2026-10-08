package com.oula.compliance;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ComplianceKernelIntegrationTest {
    @Autowired ComplianceKernel kernel;
    @Autowired JdbcTemplate jdbc;

    @Test
    void autonomousActionRequiresIndependentHumanApprovalBeforeAllow() {
        UUID workspaceId = UUID.randomUUID();
        UUID agentActor = UUID.randomUUID();
        UUID approverActor = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        seed(workspaceId);

        AccessContext agent = new AccessContext(
                agentActor, "oula-agent", workspaceId,
                AccessPurpose.AUTONOMOUS_EXECUTION
        );
        AccessContext sameHumanActor = new AccessContext(
                agentActor, "same-human", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        AccessContext approver = new AccessContext(
                approverActor, "human-approver", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );

        PolicyRequest request = new PolicyRequest(
                "SERVICE_QUOTE_SELECT", "WorkOrder", resourceId,
                "SA", new BigDecimal("4200.00"), "SAR",
                true, true, null, Map.of("source", "integration-test")
        );

        PolicyDecision first = kernel.evaluate(agent, request, UUID.randomUUID());
        assertThat(first.decision()).isEqualTo(PolicyDecision.Decision.REQUIRE_APPROVAL);
        assertThat(first.reasonCodes()).contains("POLICY_APPROVAL_REQUIRED");

        ApprovalRequest approval = kernel.requestApproval(
                agent, request, "Select qualified provider quote", UUID.randomUUID()
        );
        assertThat(approval.status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> kernel.decideApproval(
                sameHumanActor, approval.id(), true,
                "Self approval must fail", UUID.randomUUID()
        )).isInstanceOf(SecurityException.class)
          .hasMessageContaining("cannot approve");

        ApprovalRequest approved = kernel.decideApproval(
                approver, approval.id(), true,
                "Reviewed budget, provider qualification and scope",
                UUID.randomUUID()
        );
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decidedBy()).isEqualTo(approverActor);

        PolicyRequest withApproval = new PolicyRequest(
                request.action(), request.resourceType(), request.resourceId(),
                request.jurisdiction(), request.amount(), request.currency(),
                request.verificationPresent(), request.evidencePresent(),
                approval.id(), request.context()
        );
        PolicyDecision allowed = kernel.evaluate(
                agent, withApproval, UUID.randomUUID()
        );
        assertThat(allowed.decision()).isEqualTo(PolicyDecision.Decision.ALLOW);
        assertThat(allowed.reasonCodes()).contains("APPROVAL_SATISFIED");
        assertThat(count("compliance.approval_request")).isEqualTo(1);
        assertThat(count("compliance.policy_decision")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void workspacePolicyEnforcesVerificationEvidenceAndFinancialLimit() {
        UUID workspaceId = UUID.randomUUID();
        UUID adminActor = UUID.randomUUID();
        UUID agentActor = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        seed(workspaceId);

        AccessContext admin = new AccessContext(
                adminActor, "policy-admin", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        AccessContext agent = new AccessContext(
                agentActor, "oula-agent", workspaceId,
                AccessPurpose.AUTONOMOUS_EXECUTION
        );

        PolicyRule rule = kernel.createRule(
                admin,
                new CreatePolicyRuleCommand(
                        "AUTO_SETTLEMENT_REFERENCE",
                        "1",
                        AccessPurpose.AUTONOMOUS_EXECUTION,
                        "SETTLEMENT_REFERENCE_RECORD",
                        "WorkOrder",
                        "SA",
                        PolicyDecision.Decision.ALLOW,
                        new BigDecimal("1000.00"),
                        "SAR",
                        true,
                        true,
                        100,
                        null,
                        null
                ),
                UUID.randomUUID()
        );
        assertThat(rule.workspaceId()).isEqualTo(workspaceId);

        PolicyRequest missingVerification = new PolicyRequest(
                "SETTLEMENT_REFERENCE_RECORD", "WorkOrder", resourceId,
                "SA", new BigDecimal("500.00"), "SAR",
                false, false, null, Map.of()
        );
        assertThat(kernel.evaluate(agent, missingVerification, UUID.randomUUID()).decision())
                .isEqualTo(PolicyDecision.Decision.REQUIRE_VERIFICATION);

        PolicyRequest missingEvidence = new PolicyRequest(
                missingVerification.action(), missingVerification.resourceType(),
                missingVerification.resourceId(), "SA",
                new BigDecimal("500.00"), "SAR",
                true, false, null, Map.of()
        );
        assertThat(kernel.evaluate(agent, missingEvidence, UUID.randomUUID()).decision())
                .isEqualTo(PolicyDecision.Decision.REQUIRE_DOCUMENT);

        PolicyRequest belowLimit = new PolicyRequest(
                missingVerification.action(), missingVerification.resourceType(),
                missingVerification.resourceId(), "SA",
                new BigDecimal("500.00"), "SAR",
                true, true, null, Map.of()
        );
        assertThat(kernel.evaluate(agent, belowLimit, UUID.randomUUID()).decision())
                .isEqualTo(PolicyDecision.Decision.ALLOW);

        PolicyRequest aboveLimit = new PolicyRequest(
                missingVerification.action(), missingVerification.resourceType(),
                missingVerification.resourceId(), "SA",
                new BigDecimal("1500.00"), "SAR",
                true, true, null, Map.of()
        );
        PolicyDecision limited = kernel.evaluate(agent, aboveLimit, UUID.randomUUID());
        assertThat(limited.decision()).isEqualTo(PolicyDecision.Decision.REQUIRE_APPROVAL);
        assertThat(limited.reasonCodes()).contains("FINANCIAL_LIMIT_EXCEEDED");
    }

    @Test
    void humanPropertyManagementBaselineRemainsExplicitlyAllowed() {
        UUID workspaceId = UUID.randomUUID();
        seed(workspaceId);
        AccessContext human = new AccessContext(
                UUID.randomUUID(), "human-manager", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        PolicyDecision decision = kernel.evaluate(
                human,
                new PolicyRequest(
                        "WORK_ORDER_VERIFY", "WorkOrder", UUID.randomUUID(),
                        "SA", null, null, true, true, null, Map.of()
                ),
                UUID.randomUUID()
        );
        assertThat(decision.decision()).isEqualTo(PolicyDecision.Decision.ALLOW);
        assertThat(decision.reasonCodes()).contains("POLICY_ALLOW");
    }

    private void seed(UUID workspaceId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Compliance Test", "ACTIVE"
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }
}
