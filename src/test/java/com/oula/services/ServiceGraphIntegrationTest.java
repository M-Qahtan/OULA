package com.oula.services;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.*;
import com.oula.settlement.SettlementReference;
import com.oula.settlement.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ServiceGraphIntegrationTest {
    @Autowired PropertyManagementService management;
    @Autowired OperationsExecutionService execution;
    @Autowired ServiceGraphService services;
    @Autowired SettlementService settlements;
    @Autowired JdbcTemplate jdbc;

    @Test
    void closesServiceGraphFromVerifiedProviderQuoteToOutcomeAndSettlementReference() {
        UUID workspaceId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID providerPartyId = UUID.randomUUID();
        seed(workspaceId, propertyId);

        AccessContext access = new AccessContext(
                actorId, "service-graph-test", workspaceId,
                AccessPurpose.PROPERTY_MANAGEMENT
        );

        management.enroll(access, propertyId, UUID.randomUUID());
        management.createObligation(
                access, propertyId,
                new CreateObligationCommand(
                        "HVAC_MAINTENANCE", "Service HVAC",
                        Instant.now().plusSeconds(3600), "HIGH",
                        "MAINTENANCE_PLAN", "plan-08"
                ),
                UUID.randomUUID()
        );
        management.assess(access, propertyId, UUID.randomUUID());
        ActionItem action = management.overview(access, propertyId).actions().getFirst();

        WorkOrder workOrder = execution.createFromAction(
                access, action.id(),
                new CreateWorkOrderCommand(
                        "HVAC", "HVAC preventive maintenance",
                        "Inspect, clean, test and document HVAC equipment",
                        new BigDecimal("5000.00"), "SAR"
                ),
                UUID.randomUUID()
        );

        ServiceProvider provider = services.registerProvider(
                access, providerPartyId, "Verified HVAC Services", UUID.randomUUID()
        );
        services.addCapability(access, provider.id(), "hvac", UUID.randomUUID());

        assertThatThrownBy(() -> services.submitQuote(
                access, workOrder.id(), provider.id(),
                new BigDecimal("4200.00"), "SAR", 2,
                "Complete scope within two days", UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("active and verified");

        provider = services.verifyProvider(
                access, provider.id(), UUID.randomUUID(), UUID.randomUUID()
        );
        assertThat(provider.verificationStatus()).isEqualTo("VERIFIED");

        ServiceQuote quote = services.submitQuote(
                access, workOrder.id(), provider.id(),
                new BigDecimal("4200.00"), "SAR", 2,
                "Complete scope within two days", UUID.randomUUID()
        );
        assertThat(quote.status()).isEqualTo("SUBMITTED");

        ServiceQuote selected = services.selectQuote(
                access, quote.id(), UUID.randomUUID()
        );
        assertThat(selected.status()).isEqualTo("SELECTED");

        WorkOrder assigned = execution.get(access, workOrder.id());
        assertThat(assigned.status()).isEqualTo("ASSIGNED");
        assertThat(assigned.providerPartyId()).isEqualTo(providerPartyId);
        assertThat(assigned.approvedBudget()).isEqualByComparingTo("4200.00");

        execution.start(access, workOrder.id(), UUID.randomUUID());
        execution.submitCompletion(
                access, workOrder.id(),
                new SubmitWorkOrderCompletionCommand(
                        new BigDecimal("4000.00"), UUID.randomUUID(),
                        "HVAC work complete with verified completion evidence"
                ),
                UUID.randomUUID()
        );
        WorkOrder completed = execution.verifyCompletion(
                access, workOrder.id(), UUID.randomUUID()
        );
        assertThat(completed.status()).isEqualTo("COMPLETED");

        ProviderOutcome outcome = services.recordOutcome(
                access, workOrder.id(), 5,
                "Completed within scope and below selected quote",
                UUID.randomUUID()
        );
        assertThat(outcome.costVariance()).isEqualByComparingTo("-200.00");
        assertThat(outcome.rating()).isEqualTo(5);

        SettlementReference settlement = settlements.record(
                access, workOrder.id(), "TEST_PROCESSOR", "settlement-001",
                new BigDecimal("4000.00"), "SAR", "SETTLED",
                UUID.randomUUID(), UUID.randomUUID()
        );
        assertThat(settlement.status()).isEqualTo("SETTLED");

        assertThatThrownBy(() -> settlements.record(
                access, workOrder.id(), "TEST_PROCESSOR", "settlement-002",
                BigDecimal.ONE, "SAR", "SETTLED",
                UUID.randomUUID(), UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("exceeds verified actual cost");

        assertThat(count("service_graph.provider")).isEqualTo(1);
        assertThat(count("service_graph.quote")).isEqualTo(1);
        assertThat(count("service_graph.provider_outcome")).isEqualTo(1);
        assertThat(count("settlement.reference")).isEqualTo(1);
    }

    private void seed(UUID workspaceId, UUID propertyId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Service Graph Test", "ACTIVE"
        );
        jdbc.update("""
                insert into property.asset
                    (id, workspace_id, asset_type, district, bedrooms, asking_price)
                values (?,?,?,?,?,?)
                """,
                propertyId, workspaceId, "RESIDENTIAL", "Al Yasmin", 4, 1_800_000
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }
}
