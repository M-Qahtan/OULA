package com.oula.tenancy;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RentalFinancialIntegrationTest {
    @Autowired RentalFinancialService finance;
    @Autowired JdbcTemplate jdbc;

    @Test
    void documentaryReceiptsSupportPartialAllocationAndReversalWithoutCashAssertions() {
        Fixture fixture=seed();
        UUID lease=seedLease(fixture);
        UUID installment=seedInstallment(fixture,lease,LocalDate.now(ZoneId.of("Asia/Riyadh")).minusDays(30),
                new BigDecimal("1000.00"));
        UUID receiptEvidence=evidence(fixture.workspace(),"RENT_RECEIPT");
        RentEvidenceEntry receipt=finance.recordDocumentaryReceipt(
                fixture.access(),lease,installment,new BigDecimal("300.00"),
                receiptEvidence,"external-test-"+UUID.randomUUID(),
                "Landlord evidence review of a rent receipt",UUID.randomUUID());

        RentalFinancialSummary partial=finance.financialSummary(
                fixture.access(),lease,LocalDate.now(ZoneId.of("Asia/Riyadh")));
        assertThat(partial.netDocumentaryReceipts()).isEqualByComparingTo("300.00");
        assertThat(partial.uncoveredDueThroughDate()).isEqualByComparingTo("700.00");
        assertThat(partial.overdueInstallments()).isEqualTo(1);
        assertThat(partial.installments().getFirst().evidenceStatus())
                .isEqualTo("OVERDUE_DOCUMENTARY_GAP");

        assertThatThrownBy(()->finance.recordDocumentaryReceipt(
                fixture.access(),lease,installment,new BigDecimal("701.00"),
                evidence(fixture.workspace(),"RENT_RECEIPT"),null,"Over allocated",
                UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds contractual installment");

        assertThatThrownBy(()->jdbc.update(
                "update tenancy.rent_evidence_entry set amount=999 where id=?",receipt.id()))
                .isInstanceOf(DataAccessException.class);

        UUID reversalEvidence=evidence(fixture.workspace(),"RENT_RECEIPT_REVERSAL");
        RentEvidenceEntry reversal=finance.reverseDocumentaryReceipt(
                fixture.access(),lease,receipt.id(),reversalEvidence,
                "Incorrect documentary allocation",UUID.randomUUID());
        assertThat(reversal.reversesEntryId()).isEqualTo(receipt.id());
        assertThat(finance.financialSummary(fixture.access(),lease,
                LocalDate.now(ZoneId.of("Asia/Riyadh"))).netDocumentaryReceipts())
                .isEqualByComparingTo("0.00");

        assertThatThrownBy(()->finance.reverseDocumentaryReceipt(
                fixture.access(),lease,receipt.id(),evidence(fixture.workspace(),"RENT_RECEIPT_REVERSAL"),
                "Duplicate reversal",UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been reversed");

        assertThat(jdbc.queryForObject("""
                select count(*) from platform.outbox_event
                 where workspace_id=? and event_type like 'tenancy.rent.%'
                """,Integer.class,fixture.workspace())).isEqualTo(2);
    }

    @Test
    void physicalOccupancyUsesHandoverObservationsAndExcludesUnknownFromDenominator() {
        Fixture f=seed();
        UUID one=seedUnit(f,"A");
        UUID two=seedUnit(f,"B");
        seedUnit(f,"C");

        Instant now=Instant.now();
        UUID lease1=seedLease(f,one);
        UUID lease2=seedLease(f,two);
        jdbc.update("""
                insert into tenancy.occupancy
                 (id,workspace_id,unit_id,lease_id,checked_in_at,
                  check_in_evidence_id,recorded_by)
                values (?,?,?,?,?,?,?)
                """,UUID.randomUUID(),f.workspace(),one,lease1,
                utc(now.minusSeconds(86400*10)),UUID.randomUUID(),f.actor());
        jdbc.update("""
                insert into tenancy.occupancy
                 (id,workspace_id,unit_id,lease_id,checked_in_at,checked_out_at,
                  check_in_evidence_id,check_out_evidence_id,recorded_by)
                values (?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),f.workspace(),two,lease2,
                utc(now.minusSeconds(86400*15)),utc(now.minusSeconds(86400*5)),
                UUID.randomUUID(),UUID.randomUUID(),f.actor());
        PropertyOccupancyInsight current=finance.occupancyInsight(f.access(),f.property(),now);
        assertThat(current.totalUnits()).isEqualTo(3);
        assertThat(current.knownUnits()).isEqualTo(2);
        assertThat(current.occupiedRecordedUnits()).isEqualTo(1);
        assertThat(current.vacancyRecordedUnits()).isEqualTo(1);
        assertThat(current.unknownUnits()).isEqualTo(1);
        assertThat(current.evidenceCoverageRatio()).isEqualByComparingTo("0.666667");
        assertThat(current.occupiedShareOfKnown()).isEqualByComparingTo("0.500000");
        assertThat(current.coverageStatus()).isEqualTo("PARTIAL_EVIDENCE");

        PropertyOccupancyInsight historical=finance.occupancyInsight(f.access(),f.property(),
                now.minusSeconds(86400*7));
        assertThat(historical.occupiedRecordedUnits()).isEqualTo(2);
        assertThat(historical.vacancyRecordedUnits()).isZero();
        assertThat(historical.unknownUnits()).isEqualTo(1);
    }

    @Test
    void noDocumentaryEvidenceDoesNotProveZeroBankCollectionsAndOtherWorkspaceCannotRead() {
        Fixture f=seed();
        UUID lease=seedLease(f);
        seedInstallment(f,lease,LocalDate.now(ZoneId.of("Asia/Riyadh")).minusDays(1),
                new BigDecimal("1500.00"));
        RentalFinancialSummary summary=finance.financialSummary(f.access(),lease,
                LocalDate.now(ZoneId.of("Asia/Riyadh")));
        assertThat(summary.netDocumentaryReceipts()).isEqualByComparingTo("0");
        assertThat(summary.uncoveredDueThroughDate()).isEqualByComparingTo("1500");
        AccessContext other=new AccessContext(f.actor(),"foreign",UUID.randomUUID(),
                AccessPurpose.PROPERTY_MANAGEMENT);
        assertThatThrownBy(()->finance.financialSummary(other,lease,
                LocalDate.now(ZoneId.of("Asia/Riyadh"))))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    private Fixture seed() {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),actor=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Rental Finance","ACTIVE");
        jdbc.update("""
                insert into property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",4,1_600_000);
        return new Fixture(w,p,actor,
                new AccessContext(actor,"finance-test",w,AccessPurpose.PROPERTY_MANAGEMENT));
    }
    private UUID seedUnit(Fixture f,String code) {
        UUID unit=UUID.randomUUID();
        jdbc.update("""
                insert into tenancy.unit
                (id,workspace_id,property_id,unit_code,status,created_by,created_at)
                values (?,?,?,?,'ACTIVE',?,?)
                """,unit,f.workspace(),f.property(),code,f.actor(),utc(Instant.now()));
        return unit;
    }
    private UUID seedLease(Fixture f) { return seedLease(f,seedUnit(f,"UNIT-"+UUID.randomUUID())); }
    private UUID seedLease(Fixture f,UUID unit) {
        UUID lease=UUID.randomUUID();
        LocalDate start=LocalDate.now(ZoneId.of("Asia/Riyadh")).minusMonths(2);
        jdbc.update("""
                insert into tenancy.lease
                 (id,workspace_id,unit_id,landlord_party_id,tenant_party_id,
                  start_on,end_on,periodic_rent,currency,rent_every_months,
                  status,signing_evidence_id,signed_at,activated_at,created_by,created_at)
                values (?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)
                """,lease,f.workspace(),unit,UUID.randomUUID(),UUID.randomUUID(),
                start,start.plusMonths(12),new BigDecimal("1000.00"),"SAR",1,
                UUID.randomUUID(),utc(Instant.now()),utc(Instant.now()),f.actor(),utc(Instant.now()));
        return lease;
    }
    private UUID seedInstallment(Fixture f,UUID lease,LocalDate due,BigDecimal amount) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into tenancy.rent_installment
                  (id,workspace_id,lease_id,due_on,amount,currency,status,created_at)
                values (?,?,?,?,?,?,'SCHEDULED',?)
                """,id,f.workspace(),lease,due,amount,"SAR",utc(Instant.now()));
        return id;
    }
    private UUID evidence(UUID workspace,String type) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into docs.evidence
                 (id,workspace_id,evidence_type,source,verification_status,content_hash,captured_at)
                values (?,?,?,'REVIEWED_DOCUMENT','VERIFIED',?,?)
                """,id,workspace,type,UUID.randomUUID().toString(),utc(Instant.now()));
        return id;
    }
    private static OffsetDateTime utc(Instant at){
        return OffsetDateTime.ofInstant(at,ZoneOffset.UTC);
    }
    record Fixture(UUID workspace,UUID property,UUID actor,AccessContext access){}
}
