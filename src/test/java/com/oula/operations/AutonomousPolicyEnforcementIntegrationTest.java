package com.oula.operations;

import com.oula.compliance.*;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.settlement.SettlementReference;
import com.oula.settlement.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AutonomousPolicyEnforcementIntegrationTest {
    @Autowired PropertyManagementService management;
    @Autowired OperationsExecutionService execution;
    @Autowired ComplianceKernel compliance;
    @Autowired SettlementService settlements;
    @Autowired JdbcTemplate jdbc;

    @Test
    void autonomousWorkOrderTransitionsRequireActionBoundHumanApprovals() {
        Fixture fixture = fixture();

        PolicyEnforcementException blockedApproval = catchPolicy(() ->
                execution.approve(
                        fixture.agent(),
                        fixture.workOrder().id(),
                        new BigDecimal("5000.00"),
                        policy(null),
                        UUID.randomUUID()
                ));
        assertThat(blockedApproval.policyDecision().decision())
                .isEqualTo(PolicyDecision.Decision.REQUIRE_APPROVAL);
        assertThat(execution.get(fixture.human(), fixture.workOrder().id()).status())
                .isEqualTo("PENDING_APPROVAL");

        UUID approveId = approveFor(
                fixture,
                "WORK_ORDER_APPROVE",
                new BigDecimal("5000.00")
        );
        WorkOrder approved = execution.approve(
                fixture.agent(),
                fixture.workOrder().id(),
                new BigDecimal("5000.00"),
                policy(approveId),
                UUID.randomUUID()
        );
        assertThat(approved.status()).isEqualTo("APPROVED");

        PolicyEnforcementException wrongApproval = catchPolicy(() ->
                execution.assign(
                        fixture.agent(),
                        fixture.workOrder().id(),
                        fixture.providerPartyId(),
                        policy(approveId),
                        UUID.randomUUID()
                ));
        assertThat(wrongApproval.policyDecision().decision())
                .isEqualTo(PolicyDecision.Decision.REQUIRE_APPROVAL);
        assertThat(wrongApproval.policyDecision().reasonCodes())
                .contains("POLICY_APPROVAL_REQUIRED");

        UUID assignId = approveFor(
                fixture,
                "WORK_ORDER_ASSIGN",
                new BigDecimal("5000.00")
        );
        WorkOrder assigned = execution.assign(
                fixture.agent(),
                fixture.workOrder().id(),
                fixture.providerPartyId(),
                policy(assignId),
                UUID.randomUUID()
        );
        assertThat(assigned.status()).isEqualTo("ASSIGNED");

        UUID startId = approveFor(
                fixture,
                "WORK_ORDER_START",
                new BigDecimal("5000.00")
        );
        WorkOrder started = execution.start(
                fixture.agent(),
                fixture.workOrder().id(),
                policy(startId),
                UUID.randomUUID()
        );
        assertThat(started.status()).isEqualTo("IN_PROGRESS");

        execution.submitCompletion(
                fixture.human(),
                fixture.workOrder().id(),
                new SubmitWorkOrderCompletionCommand(
                        new BigDecimal("4700.00"),
                        UUID.randomUUID(),
                        "Completion evidence submitted by human operator"
                ),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> execution.verifyCompletion(
                fixture.agent(),
                fixture.workOrder().id(),
                UUID.randomUUID()
        )).isInstanceOf(SecurityException.class)
          .hasMessageContaining("PROPERTY_MANAGEMENT");

        WorkOrder completed = execution.verifyCompletion(
                fixture.human(),
                fixture.workOrder().id(),
                UUID.randomUUID()
        );
        assertThat(completed.status()).isEqualTo("COMPLETED");

        assertThat(count("compliance.approval_request")).isEqualTo(3);
        assertThat(count("compliance.policy_decision")).isGreaterThanOrEqualTo(8);
    }

    @Test
    void autonomousSettlementIsBlockedUntilExactApprovalThenRecorded() {
        Fixture fixture = fixture();

        execution.approve(
                fixture.human(), fixture.workOrder().id(),
                new BigDecimal("5000.00"), UUID.randomUUID()
        );
        execution.assign(
                fixture.human(), fixture.workOrder().id(),
                fixture.providerPartyId(), UUID.randomUUID()
        );
        execution.start(
                fixture.human(), fixture.workOrder().id(), UUID.randomUUID()
        );
        execution.submitCompletion(
                fixture.human(), fixture.workOrder().id(),
                new SubmitWorkOrderCompletionCommand(
                        new BigDecimal("4700.00"),
                        UUID.randomUUID(),
                        "Human submitted completion evidence"
                ),
                UUID.randomUUID()
        );
        execution.verifyCompletion(
                fixture.human(), fixture.workOrder().id(), UUID.randomUUID()
        );

        UUID evidenceId = UUID.randomUUID();
        PolicyEnforcementException blocked = catchPolicy(() ->
                settlements.record(
                        fixture.agent(),
                        fixture.workOrder().id(),
                        "TEST_PROCESSOR",
                        "auto-settlement-001",
                        new BigDecimal("4700.00"),
                        "SAR",
                        "SETTLED",
                        evidenceId,
                        policy(null),
                        UUID.randomUUID()
                ));
        assertThat(blocked.policyDecision().decision())
                .isEqualTo(PolicyDecision.Decision.REQUIRE_APPROVAL);
        assertThat(count("settlement.reference")).isZero();

        UUID approvalId = approveFor(
                fixture,
                "SETTLEMENT_REFERENCE_RECORD",
                new BigDecimal("4700.00")
        );
        SettlementReference reference = settlements.record(
                fixture.agent(),
                fixture.workOrder().id(),
                "TEST_PROCESSOR",
                "auto-settlement-001",
                new BigDecimal("4700.00"),
                "SAR",
                "SETTLED",
                evidenceId,
                policy(approvalId),
                UUID.randomUUID()
        );

        assertThat(reference.status()).isEqualTo("SETTLED");
        assertThat(reference.recordedBy()).isEqualTo(fixture.agent().actorId());
        assertThat(count("settlement.reference")).isEqualTo(1);
    }

    private UUID approveFor(
            Fixture fixture,
            String action,
            BigDecimal amount
    ) {
        PolicyRequest request = new PolicyRequest(
                action,
                "WorkOrder",
                fixture.workOrder().id(),
                "SA",
                amount,
                "SAR",
                "SETTLEMENT_REFERENCE_RECORD".equals(action),
                "SETTLEMENT_REFERENCE_RECORD".equals(action),
                null,
                Map.of("test", "wave10")
        );
        ApprovalRequest approval = compliance.requestApproval(
                fixture.agent(),
                request,
                "Wave 10 autonomous execution approval",
                UUID.randomUUID()
        );
        ApprovalRequest decided = compliance.decideApproval(
                fixture.approver(),
                approval.id(),
                true,
                "Independent human review completed",
                UUID.randomUUID()
        );
        assertThat(decided.status()).isEqualTo("APPROVED");
        return approval.id();
    }

    private PolicyEnforcementContext policy(UUID approvalId) {
        return new PolicyEnforcementContext(
                "SA", approvalId, Map.of("test", "wave10")
        );
    }

    private PolicyEnforcementException catchPolicy(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected PolicyEnforcementException");
        } catch (PolicyEnforcementException ex) {
            return ex;
        }
    }

    private Fixture fixture() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID humanActor = UUID.randomUUID();
        UUID agentActor = UUID.randomUUID();
        UUID approverActor = UUID.randomUUID();
        UUID providerPartyId = UUID.randomUUID();

        seed(workspaceId, propertyId);

        AccessContext human = new AccessContext(
                humanActor, "human-operator", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );
        AccessContext agent = new AccessContext(
                agentActor, "oula-agent", workspaceId,
                AccessPurpose.AUTONOMOUS_EXECUTION
        );
        AccessContext approver = new AccessContext(
                approverActor, "human-approver", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );

        management.enroll(human, propertyId, UUID.randomUUID());
        management.createObligation(
                human,
                propertyId,
                new CreateObligationCommand(
                        "HVAC_MAINTENANCE",
                        "Service HVAC",
                        Instant.now().plusSeconds(3600),
                        "HIGH",
                        "MAINTENANCE_PLAN",
                        "wave10-plan"
                ),
                UUID.randomUUID()
        );
        management.assess(human, propertyId, UUID.randomUUID());
        ActionItem action = management.overview(human, propertyId)
                .actions().getFirst();

        WorkOrder workOrder = execution.createFromAction(
                human,
                action.id(),
                new CreateWorkOrderCommand(
                        "HVAC",
                        "HVAC maintenance",
                        "Inspect, service and document HVAC equipment",
                        new BigDecimal("4500.00"),
                        "SAR"
                ),
                UUID.randomUUID()
        );

        return new Fixture(
                human, agent, approver, workOrder, providerPartyId
        );
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Wave 10 Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL",
                "Al Yasmin", 4, 1_800_000
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table, Long.class
        );
        return value == null ? 0 : value;
    }

    private record Fixture(
            AccessContext human,
            AccessContext agent,
            AccessContext approver,
            WorkOrder workOrder,
            UUID providerPartyId
    ) {}
}
