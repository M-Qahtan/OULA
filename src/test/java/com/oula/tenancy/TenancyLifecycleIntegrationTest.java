package com.oula.tenancy;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.PropertyManagementService;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TenancyLifecycleIntegrationTest {
    @Autowired TenancyService tenancy;
    @Autowired PropertyManagementService management;
    @Autowired JdbcTemplate jdbc;

    @Test
    void evidenceBackedLeaseOccupancyRenewalAndGuardianAreSeparateTruths() {
        Fixture f=seed();
        RentalUnit unit=tenancy.registerUnit(f.access(),f.property(),"A-101",UUID.randomUUID());
        LocalDate start=today().minusDays(1);
        Lease draft=tenancy.createLease(f.access(),unit.id(),
                new CreateLeaseCommand(UUID.randomUUID(),UUID.randomUUID(),
                        start,start.plusMonths(3),new BigDecimal("3700.00"),"SAR",1),
                UUID.randomUUID());
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(tenancy.occupancyByProperty(f.access(),f.property()).getFirst()
                .occupancyStatus()).isEqualTo("UNKNOWN");

        UUID unverified=evidence(f.workspace(),"LEASE_CONTRACT","UNVERIFIED");
        assertThatThrownBy(() -> tenancy.sign(f.access(),draft.id(),unverified,UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
        UUID signing=evidence(f.workspace(),"LEASE_CONTRACT","VERIFIED");
        Lease signed=tenancy.sign(f.access(),draft.id(),signing,UUID.randomUUID());
        assertThat(signed.status()).isEqualTo("SIGNED");
        assertThat(tenancy.occupancyByProperty(f.access(),f.property()).getFirst()
                .occupancyStatus()).isEqualTo("UNKNOWN");

        Lease competing=tenancy.createLease(f.access(),unit.id(),
                new CreateLeaseCommand(UUID.randomUUID(),UUID.randomUUID(),
                        start.plusMonths(1),start.plusMonths(4),
                        new BigDecimal("3800.00"),"SAR",1),UUID.randomUUID());
        assertThatThrownBy(() -> tenancy.sign(f.access(),competing.id(),signing,UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlaps");

        Lease active=tenancy.activate(f.access(),draft.id(),UUID.randomUUID());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(tenancy.rentSchedule(f.access(),draft.id())).hasSize(3);
        assertThat(tenancy.rentSchedule(f.access(),draft.id()).getFirst()
                .status()).isEqualTo("SCHEDULED");
        assertThat(management.overview(f.access(),f.property()).obligations())
                .anySatisfy(o -> {
                    assertThat(o.obligationType()).isEqualTo("LEASE_RENEWAL_REVIEW");
                    assertThat(o.sourceReference()).contains(draft.id().toString());
                });

        UUID moveIn=evidence(f.workspace(),"HANDOVER_RECORD","VERIFIED");
        Occupancy occ=tenancy.checkIn(f.access(),draft.id(),moveIn,UUID.randomUUID());
        assertThat(occ.checkedOutAt()).isNull();
        assertThat(tenancy.occupancyByProperty(f.access(),f.property()).getFirst()
                .occupancyStatus()).isEqualTo("OCCUPIED_RECORDED");
        assertThatThrownBy(() -> tenancy.endLease(f.access(),draft.id(),
                signing,UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("check out");

        UUID renewalEvidence=evidence(f.workspace(),"RENEWAL_DECISION","VERIFIED");
        RenewalDecision renewal=tenancy.recordRenewal(
                f.access(),draft.id(),"RENEWAL_ACCEPTED","Tenant and owner intend to renew",
                renewalEvidence,UUID.randomUUID());
        assertThat(renewal.decision()).isEqualTo("RENEWAL_ACCEPTED");
        assertThat(tenancy.lease(f.access(),draft.id()).endOn()).isEqualTo(start.plusMonths(3));
        assertThat(tenancy.renewalHistory(f.access(),draft.id())).hasSize(1);

        UUID returnEvidence=evidence(f.workspace(),"HANDOVER_RETURN","VERIFIED");
        Occupancy closed=tenancy.checkOut(f.access(),draft.id(),returnEvidence,UUID.randomUUID());
        assertThat(closed.checkedOutAt()).isNotNull();
        assertThat(tenancy.occupancyByProperty(f.access(),f.property()).getFirst()
                .occupancyStatus()).isEqualTo("VACANCY_RECORDED");
        UUID termination=evidence(f.workspace(),"LEASE_TERMINATION","VERIFIED");
        assertThat(tenancy.endLease(f.access(),draft.id(),termination,UUID.randomUUID())
                .status()).isEqualTo("ENDED");

        assertThat(jdbc.queryForObject("""
                select count(*) from platform.outbox_event
                 where workspace_id=? and event_type like 'tenancy.%'
                """,Integer.class,f.workspace())).isGreaterThanOrEqualTo(7);
        // History of the rental schedule is not falsely marked paid on lease end.
        assertThat(tenancy.rentSchedule(f.access(),draft.id()))
                .allMatch(i -> "SCHEDULED".equals(i.status()));
        // Immutable renewal decisions cannot be retroactively rewritten.
        assertThatThrownBy(() -> jdbc.update("""
                update tenancy.renewal_decision set decision='RENEWAL_DECLINED' where id=?
                """,renewal.id())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void leaseIsWorkspaceBoundAndUnsignedCannotRecordOccupancy() {
        Fixture f=seed();
        RentalUnit unit=tenancy.registerUnit(f.access(),f.property(),"B-202",UUID.randomUUID());
        LocalDate start=today();
        Lease draft=tenancy.createLease(f.access(),unit.id(),new CreateLeaseCommand(
                UUID.randomUUID(),UUID.randomUUID(),start,start.plusMonths(1),
                new BigDecimal("1000"),"SAR",1),UUID.randomUUID());
        assertThatThrownBy(()->tenancy.checkIn(f.access(),draft.id(),UUID.randomUUID(),
                UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        AccessContext other=new AccessContext(f.access().actorId(),"other-workspace",
                UUID.randomUUID(),AccessPurpose.PROPERTY_MANAGEMENT);
        assertThatThrownBy(()->tenancy.lease(other,draft.id()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(tenancy.cancelDraft(f.access(),draft.id(),UUID.randomUUID())
                .status()).isEqualTo("CANCELLED");
    }

    private Fixture seed() {
        UUID w=UUID.randomUUID(),p=UUID.randomUUID(),actor=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,?,?,?)",
                w,"PERSONAL","Tenancy Core","ACTIVE");
        jdbc.update("""
                insert into property.asset
                    (id,workspace_id,asset_type,district,bedrooms,asking_price)
                values (?,?,?,?,?,?)
                """,p,w,"RESIDENTIAL","Al Yasmin",3,1_800_000);
        AccessContext access=new AccessContext(actor,"tenancy-test",w,
                AccessPurpose.PROPERTY_MANAGEMENT);
        management.enroll(access,p,UUID.randomUUID());
        return new Fixture(w,p,access);
    }

    private UUID evidence(UUID workspace,String type,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into docs.evidence(id,workspace_id,evidence_type,source,
                                          verification_status,content_hash,captured_at)
                values (?,?,?,?,?,?,?)
                """,id,workspace,type,"HUMAN_REVIEW",status,UUID.randomUUID().toString(),
                OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC));
        return id;
    }
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Riyadh"));}
    record Fixture(UUID workspace, UUID property, AccessContext access){}
}
