package com.oula.tenancy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class TenancyRepository {
    private final JdbcClient jdbc;
    TenancyRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void requireProperty(UUID workspaceId, UUID propertyId) {
        jdbc.sql("select id from property.asset where workspace_id=:w and id=:p")
                .param("w", workspaceId).param("p", propertyId)
                .query(UUID.class).optional()
                .orElseThrow(() -> new NoSuchElementException("property not found in workspace"));
    }

    void insertUnit(RentalUnit u) {
        jdbc.sql("""
                insert into tenancy.unit
                    (id, workspace_id, property_id, unit_code, status, created_by, created_at)
                values (:id,:w,:p,:code,:status,:actor,:at)
                """)
                .param("id",u.id()).param("w",u.workspaceId()).param("p",u.propertyId())
                .param("code",u.unitCode()).param("status",u.status())
                .param("actor",u.createdBy()).param("at",utc(u.createdAt()))
                .update();
    }

    RentalUnit unit(UUID workspaceId, UUID unitId, boolean lock) {
        String sql = """
                select * from tenancy.unit where workspace_id=:w and id=:id
                """ + (lock ? " for update" : "");
        return jdbc.sql(sql).param("w",workspaceId).param("id",unitId)
                .query((rs, row) -> new RentalUnit(
                        rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                        rs.getObject("property_id",UUID.class),rs.getString("unit_code"),
                        rs.getString("status"),rs.getObject("created_by",UUID.class),
                        instant(rs.getObject("created_at",OffsetDateTime.class))))
                .optional().orElseThrow(() -> new NoSuchElementException("unit not found"));
    }

    void insertLease(Lease l) {
        jdbc.sql("""
                insert into tenancy.lease (
                    id,workspace_id,unit_id,landlord_party_id,tenant_party_id,
                    start_on,end_on,periodic_rent,currency,rent_every_months,
                    status,created_by,created_at,version)
                values (:id,:w,:unit,:landlord,:tenant,:start,:end,:rent,:currency,:months,
                        :status,:actor,:at,0)
                """)
                .param("id",l.id()).param("w",l.workspaceId()).param("unit",l.unitId())
                .param("landlord",l.landlordPartyId()).param("tenant",l.tenantPartyId())
                .param("start",l.startOn()).param("end",l.endOn())
                .param("rent",l.periodicRent()).param("currency",l.currency())
                .param("months",l.rentEveryMonths()).param("status",l.status())
                .param("actor",l.createdBy()).param("at",utc(l.createdAt()))
                .update();
    }

    Lease lease(UUID workspaceId, UUID leaseId, boolean lock) {
        String sql = "select * from tenancy.lease where workspace_id=:w and id=:id"
                + (lock ? " for update" : "");
        return jdbc.sql(sql).param("w",workspaceId).param("id",leaseId)
                .query((rs,row) -> mapLease(rs)).optional()
                .orElseThrow(() -> new NoSuchElementException("lease not found"));
    }

    List<Lease> leasesForUnit(UUID workspaceId, UUID unitId) {
        unit(workspaceId, unitId, false);
        return jdbc.sql("""
                select * from tenancy.lease
                 where workspace_id=:w and unit_id=:unit
                 order by start_on desc, created_at desc
                """)
                .param("w",workspaceId).param("unit",unitId)
                .query((rs,row)->mapLease(rs)).list();
    }

    boolean hasOverlappingCommittedLease(Lease lease) {
        return jdbc.sql("""
                select exists(
                    select 1 from tenancy.lease
                     where unit_id=:unit and id<>:id
                       and status in ('SIGNED','ACTIVE')
                       and daterange(start_on,end_on,'[)')
                          && daterange(:start::date,:end::date,'[)')
                )
                """)
                .param("unit",lease.unitId()).param("id",lease.id())
                .param("start",lease.startOn()).param("end",lease.endOn())
                .query(Boolean.class).single();
    }

    Lease sign(Lease current, UUID evidenceId, Instant now) {
        int changed=jdbc.sql("""
                update tenancy.lease set status='SIGNED',
                  signing_evidence_id=:evidence, signed_at=:at, version=version+1
                 where id=:id and workspace_id=:w and status='DRAFT' and version=:version
                """)
                .param("evidence",evidenceId).param("at",utc(now))
                .param("id",current.id()).param("w",current.workspaceId())
                .param("version",current.version()).update();
        requireUpdated(changed);
        return lease(current.workspaceId(),current.id(),false);
    }

    Lease activate(Lease current, Instant now) {
        int changed=jdbc.sql("""
                update tenancy.lease set status='ACTIVE',
                  activated_at=:at, version=version+1
                 where id=:id and workspace_id=:w and status='SIGNED' and version=:version
                """)
                .param("at",utc(now)).param("id",current.id())
                .param("w",current.workspaceId()).param("version",current.version()).update();
        requireUpdated(changed);
        return lease(current.workspaceId(),current.id(),false);
    }

    Lease end(Lease current, UUID evidenceId, Instant now) {
        int changed=jdbc.sql("""
                update tenancy.lease set status='ENDED',
                  termination_evidence_id=:evidence, ended_at=:at, version=version+1
                 where id=:id and workspace_id=:w and status='ACTIVE' and version=:version
                """)
                .param("evidence",evidenceId).param("at",utc(now))
                .param("id",current.id()).param("w",current.workspaceId())
                .param("version",current.version()).update();
        requireUpdated(changed);
        return lease(current.workspaceId(),current.id(),false);
    }

    Lease cancelDraft(Lease current) {
        int changed=jdbc.sql("""
                update tenancy.lease set status='CANCELLED',version=version+1
                 where id=:id and workspace_id=:w and status='DRAFT' and version=:version
                """)
                .param("id",current.id()).param("w",current.workspaceId())
                .param("version",current.version()).update();
        requireUpdated(changed);
        return lease(current.workspaceId(),current.id(),false);
    }

    void insertInstallments(List<RentInstallment> dues) {
        for (RentInstallment due : dues) {
            jdbc.sql("""
                    insert into tenancy.rent_installment
                     (id,workspace_id,lease_id,due_on,amount,currency,status,created_at)
                    values (:id,:w,:lease,:due,:amount,:currency,'SCHEDULED',:at)
                    """)
                    .param("id",due.id()).param("w",due.workspaceId())
                    .param("lease",due.leaseId()).param("due",due.dueOn())
                    .param("amount",due.amount()).param("currency",due.currency())
                    .param("at",utc(due.createdAt())).update();
        }
    }

    List<RentInstallment> installments(UUID workspaceId,UUID leaseId) {
        lease(workspaceId,leaseId,false);
        return jdbc.sql("""
                select * from tenancy.rent_installment
                 where workspace_id=:w and lease_id=:id order by due_on
                """)
                .param("w",workspaceId).param("id",leaseId)
                .query((rs,row)->new RentInstallment(
                        rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                        rs.getObject("lease_id",UUID.class),rs.getObject("due_on",LocalDate.class),
                        rs.getBigDecimal("amount"),rs.getString("currency"),
                        rs.getString("status"),instant(rs.getObject("created_at",OffsetDateTime.class))))
                .list();
    }

    Occupancy activeOccupancy(UUID workspaceId, UUID leaseId) {
        return jdbc.sql("""
                select * from tenancy.occupancy
                 where workspace_id=:w and lease_id=:id and checked_out_at is null
                """)
                .param("w",workspaceId).param("id",leaseId)
                .query((rs,row)->mapOccupancy(rs)).optional().orElse(null);
    }

    void checkIn(Occupancy occ) {
        jdbc.sql("""
                insert into tenancy.occupancy
                 (id,workspace_id,unit_id,lease_id,checked_in_at,
                  check_in_evidence_id,recorded_by)
                values (:id,:w,:unit,:lease,:at,:evidence,:actor)
                """)
                .param("id",occ.id()).param("w",occ.workspaceId())
                .param("unit",occ.unitId()).param("lease",occ.leaseId())
                .param("at",utc(occ.checkedInAt())).param("evidence",occ.checkInEvidenceId())
                .param("actor",occ.recordedBy()).update();
    }

    Occupancy checkOut(Occupancy existing, UUID evidenceId, Instant now) {
        int changed=jdbc.sql("""
                update tenancy.occupancy
                   set checked_out_at=:at,check_out_evidence_id=:evidence
                 where id=:id and workspace_id=:w and checked_out_at is null
                """)
                .param("id",existing.id()).param("w",existing.workspaceId())
                .param("at",utc(now)).param("evidence",evidenceId).update();
        requireUpdated(changed);
        return jdbc.sql("select * from tenancy.occupancy where id=:id and workspace_id=:w")
                .param("id",existing.id()).param("w",existing.workspaceId())
                .query((rs,row)->mapOccupancy(rs)).single();
    }

    void insertRenewal(RenewalDecision d) {
        jdbc.sql("""
                insert into tenancy.renewal_decision
                 (id,workspace_id,lease_id,decision,rationale,evidence_id,actor_id,recorded_at)
                values (:id,:w,:lease,:decision,:rationale,:evidence,:actor,:at)
                """)
                .param("id",d.id()).param("w",d.workspaceId())
                .param("lease",d.leaseId()).param("decision",d.decision())
                .param("rationale",d.rationale()).param("evidence",d.evidenceId())
                .param("actor",d.actorId()).param("at",utc(d.recordedAt()))
                .update();
    }

    List<RenewalDecision> renewals(UUID workspaceId, UUID leaseId) {
        lease(workspaceId,leaseId,false);
        return jdbc.sql("""
                select * from tenancy.renewal_decision
                 where workspace_id=:w and lease_id=:lease order by recorded_at,id
                """)
                .param("w",workspaceId).param("lease",leaseId)
                .query((rs,row)->new RenewalDecision(
                        rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                        rs.getObject("lease_id",UUID.class),rs.getString("decision"),
                        rs.getString("rationale"),rs.getObject("evidence_id",UUID.class),
                        rs.getObject("actor_id",UUID.class),
                        instant(rs.getObject("recorded_at",OffsetDateTime.class))))
                .list();
    }


    List<UnitOccupancyView> propertyOccupancy(UUID workspaceId, UUID propertyId) {
        requireProperty(workspaceId, propertyId);
        return jdbc.sql("""
                select u.id as unit_id, u.property_id, u.unit_code,
                       o.lease_id, o.checked_in_at, o.checked_out_at
                  from tenancy.unit u
                  left join lateral (
                      select occ.lease_id, occ.checked_in_at, occ.checked_out_at
                        from tenancy.occupancy occ
                       where occ.workspace_id = u.workspace_id
                         and occ.unit_id = u.id
                       order by occ.checked_in_at desc
                       limit 1
                  ) o on true
                 where u.workspace_id=:w and u.property_id=:p
                 order by u.unit_code
                """)
                .param("w",workspaceId).param("p",propertyId)
                .query((rs,row)->{
                    Instant in=instant(rs.getObject("checked_in_at",OffsetDateTime.class));
                    Instant out=instant(rs.getObject("checked_out_at",OffsetDateTime.class));
                    String status=in==null ? "UNKNOWN"
                            : out==null ? "OCCUPIED_RECORDED" : "VACANCY_RECORDED";
                    return new UnitOccupancyView(
                            rs.getObject("unit_id",UUID.class),
                            rs.getObject("property_id",UUID.class),
                            rs.getString("unit_code"),status,
                            rs.getObject("lease_id",UUID.class),in,out);
                }).list();
    }

    private Lease mapLease(ResultSet rs) throws SQLException {
        return new Lease(
                rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                rs.getObject("unit_id",UUID.class),
                rs.getObject("landlord_party_id",UUID.class),
                rs.getObject("tenant_party_id",UUID.class),
                rs.getObject("start_on",LocalDate.class),
                rs.getObject("end_on",LocalDate.class),rs.getBigDecimal("periodic_rent"),
                rs.getString("currency"),rs.getInt("rent_every_months"),
                rs.getString("status"),rs.getObject("signing_evidence_id",UUID.class),
                instant(rs.getObject("signed_at",OffsetDateTime.class)),
                instant(rs.getObject("activated_at",OffsetDateTime.class)),
                rs.getObject("termination_evidence_id",UUID.class),
                instant(rs.getObject("ended_at",OffsetDateTime.class)),
                rs.getObject("created_by",UUID.class),
                instant(rs.getObject("created_at",OffsetDateTime.class)),
                rs.getLong("version"));
    }

    private Occupancy mapOccupancy(ResultSet rs) throws SQLException {
        return new Occupancy(
                rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                rs.getObject("unit_id",UUID.class),rs.getObject("lease_id",UUID.class),
                instant(rs.getObject("checked_in_at",OffsetDateTime.class)),
                rs.getObject("check_in_evidence_id",UUID.class),
                instant(rs.getObject("checked_out_at",OffsetDateTime.class)),
                rs.getObject("check_out_evidence_id",UUID.class),
                rs.getObject("recorded_by",UUID.class));
    }

    private void requireUpdated(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("tenancy transition was concurrent or invalid");
        }
    }
    private static OffsetDateTime utc(Instant time) {
        return time == null ? null : OffsetDateTime.ofInstant(time,ZoneOffset.UTC);
    }
    private static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
