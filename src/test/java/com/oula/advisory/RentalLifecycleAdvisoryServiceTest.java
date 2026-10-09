package com.oula.advisory;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.tenancy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalLifecycleAdvisoryServiceTest {
    private final Instant now = Instant.parse("2026-10-10T04:00:00Z");
    private final UUID workspace = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID property = UUID.randomUUID();
    private final UUID knownUnit = UUID.randomUUID();
    private final UUID unknownUnit = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final AccessContext access =
            new AccessContext(actor, "risk-review", workspace, AccessPurpose.PROPERTY_MANAGEMENT);

    private TenancyService tenancy;
    private RentalFinancialService finance;
    private RentalLifecycleAdvisoryService service;

    @BeforeEach
    void setup() {
        tenancy = mock(TenancyService.class);
        finance = mock(RentalFinancialService.class);
        service = new RentalLifecycleAdvisoryService(
                tenancy, finance, Clock.fixed(now, ZoneId.of("UTC")));
    }

    @Test
    void flagsEvidenceGapAndAcceptedRenewalWithoutClaimingBankNonpayment() {
        when(finance.occupancyInsight(access, property, now)).thenReturn(
                new PropertyOccupancyInsight(property, now, 2, 1, 1, 0, 1,
                        new BigDecimal("0.500000"), BigDecimal.ONE, "PARTIAL_EVIDENCE"));
        when(tenancy.occupancyByProperty(access, property)).thenReturn(List.of(
                new UnitOccupancyView(unknownUnit, property, "B", "UNKNOWN", null, null, null),
                new UnitOccupancyView(knownUnit, property, "A", "OCCUPIED_RECORDED",
                        leaseId, now.minusSeconds(3600), null)
        ));
        when(tenancy.leasesForUnit(access, unknownUnit)).thenReturn(List.of());
        when(tenancy.leasesForUnit(access, knownUnit)).thenReturn(List.of(activeLease()));
        when(tenancy.renewalHistory(access, leaseId)).thenReturn(List.of(
                new RenewalDecision(UUID.randomUUID(), workspace, leaseId,
                        "RENEWAL_ACCEPTED", "Intent only", UUID.randomUUID(), actor, now)
        ));
        when(finance.financialSummary(access, leaseId, LocalDate.of(2026, 10, 10)))
                .thenReturn(new RentalFinancialSummary(leaseId, LocalDate.of(2026,10,10), "SAR",
                        new BigDecimal("2000"), new BigDecimal("600"),
                        new BigDecimal("1400"), 2,
                        List.of(new RentEvidencePosition(UUID.randomUUID(),leaseId,
                                LocalDate.of(2026,9,1),"SAR",new BigDecimal("2000"),
                                new BigDecimal("600"),new BigDecimal("1400"),
                                "OVERDUE_DOCUMENTARY_GAP"))));

        RentalLifecycleAdvisory result = service.recommend(access, property);

        assertThat(result.rulesVersion()).isEqualTo("rental-lifecycle-advisory-v1");
        assertThat(result.assessmentState()).isEqualTo("PARTIAL_OCCUPANCY_EVIDENCE");
        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.recommendations())
                .extracting(AdvisoryItem::actionCode)
                .contains("VERIFY_SIGNED_RENEWAL_INSTRUMENT",
                        "REVIEW_RENT_DOCUMENTARY_COVERAGE", "VERIFY_UNIT_HANDOVER_RECORD");
        assertThat(result.recommendations().getFirst().priority()).isEqualTo("HIGH");
        assertThat(result.recommendations())
                .allSatisfy(item -> {
                    assertThat(item.executionGate()).isEqualTo("HUMAN_REVIEW_REQUIRED");
                    assertThat(item.observedEvidence())
                            .extracting(AdvisoryItem.EvidenceMetric::name).contains("unitId");
                });
        assertThat(result.limitations()).contains("DOCUMENTARY_COVERAGE_NOT_BANK_SETTLEMENT");
        verify(finance, never()).recordDocumentaryReceipt(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test
    void emptyPortfolioProducesNoVacancyOrFabricatedRisk() {
        when(finance.occupancyInsight(access, property, now)).thenReturn(
                new PropertyOccupancyInsight(property, now,0,0,0,0,0,
                        null,null,"NO_UNITS"));
        when(tenancy.occupancyByProperty(access, property)).thenReturn(List.of());

        RentalLifecycleAdvisory result = service.recommend(access, property);
        assertThat(result.assessmentState()).isEqualTo("NO_UNITS");
        assertThat(result.recommendations()).isEmpty();
        assertThat(result.totalUnits()).isZero();
    }

    @Test
    void foreignPurposeIsRejectedBeforeReadingPrivateRentalFacts() {
        AccessContext unauthorized = new AccessContext(actor, "other", workspace,
                AccessPurpose.PROPERTY_DECISION_SUPPORT);
        assertThatThrownBy(() -> service.recommend(unauthorized, property))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(tenancy, finance);
    }

    @Test
    void raceBetweenSourceQueriesIsNotPresentedAsTrustedAdvice() {
        when(finance.occupancyInsight(access, property, now)).thenReturn(
                new PropertyOccupancyInsight(property, now,1,0,0,0,1,
                        BigDecimal.ZERO,null,"NO_EVIDENCE"));
        when(tenancy.occupancyByProperty(access, property)).thenReturn(List.of());
        assertThatThrownBy(() -> service.recommend(access, property))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed during assessment");
    }

    private Lease activeLease() {
        return new Lease(leaseId, workspace, knownUnit,
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026,9,10), LocalDate.of(2026,10,21),
                new BigDecimal("2000"), "SAR",1,"ACTIVE",
                UUID.randomUUID(),now.minusSeconds(86400),now.minusSeconds(80000),
                null,null,actor,now.minusSeconds(86400),1L);
    }
}
