package com.oula.tenancy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class RentalFinancialRepository {
    private final JdbcClient jdbc;
    RentalFinancialRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    RentInstallment lockedInstallment(UUID workspaceId, UUID leaseId, UUID installmentId) {
        return jdbc.sql("""
                select * from tenancy.rent_installment
                 where workspace_id=:w and lease_id=:l and id=:i
                 for update
                """)
                .param("w",workspaceId).param("l",leaseId).param("i",installmentId)
                .query((rs,row)->new RentInstallment(
                        rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                        rs.getObject("lease_id",UUID.class),rs.getObject("due_on",LocalDate.class),
                        rs.getBigDecimal("amount"),rs.getString("currency"),
                        rs.getString("status"),instant(rs.getObject("created_at",OffsetDateTime.class))))
                .optional().orElseThrow(()->new NoSuchElementException("installment not found"));
    }

    BigDecimal netDocumentaryReceipts(UUID workspaceId, UUID leaseId, UUID installmentId) {
        BigDecimal total=jdbc.sql("""
                select coalesce(sum(
                  case entry_type when 'RECEIPT' then amount else -amount end
                ),0) from tenancy.rent_evidence_entry
                 where workspace_id=:w and lease_id=:l and installment_id=:i
                """)
                .param("w",workspaceId).param("l",leaseId).param("i",installmentId)
                .query(BigDecimal.class).single();
        return total;
    }

    RentEvidenceEntry entry(UUID workspaceId,UUID leaseId,UUID entryId) {
        return jdbc.sql("""
                select * from tenancy.rent_evidence_entry
                 where workspace_id=:w and lease_id=:l and id=:id
                """)
                .param("w",workspaceId).param("l",leaseId).param("id",entryId)
                .query((rs,row)->map(rs))
                .optional().orElseThrow(()->new NoSuchElementException("receipt record not found"));
    }

    boolean hasReversal(UUID entryId) {
        return jdbc.sql("""
                select exists(select 1 from tenancy.rent_evidence_entry
                        where reverses_entry_id=:id)
                """).param("id",entryId).query(Boolean.class).single();
    }

    void append(RentEvidenceEntry entry) {
        jdbc.sql("""
                insert into tenancy.rent_evidence_entry
                    (id, workspace_id, lease_id, installment_id, entry_type,
                     amount, currency, evidence_id, external_reference,
                     reverses_entry_id, note, recorded_by, recorded_at)
                values (:id,:w,:lease,:installment,:type,
                        :amount,:currency,:evidence,:reference,:reverses,:note,:actor,:at)
                """)
                .param("id",entry.id()).param("w",entry.workspaceId())
                .param("lease",entry.leaseId()).param("installment",entry.installmentId())
                .param("type",entry.entryType()).param("amount",entry.amount())
                .param("currency",entry.currency()).param("evidence",entry.evidenceId())
                .param("reference",entry.externalReference())
                .param("reverses",entry.reversesEntryId()).param("note",entry.note())
                .param("actor",entry.recordedBy()).param("at",utc(entry.recordedAt()))
                .update();
    }

    List<RentEvidencePosition> positions(UUID workspaceId, UUID leaseId, LocalDate asOf) {
        return jdbc.sql("""
                select i.id,i.lease_id,i.due_on,i.amount,i.currency,
                       coalesce(sum(
                           case e.entry_type when 'RECEIPT' then e.amount
                                             when 'REVERSAL' then -e.amount
                                             else 0 end
                       ),0) as net_evidenced
                  from tenancy.rent_installment i
                  left join tenancy.rent_evidence_entry e
                    on e.workspace_id=i.workspace_id and e.lease_id=i.lease_id
                   and e.installment_id=i.id
                   and e.recorded_at < :cutoff
                 where i.workspace_id=:w and i.lease_id=:l
                 group by i.id,i.lease_id,i.due_on,i.amount,i.currency
                 order by i.due_on
                """)
                .param("w",workspaceId).param("l",leaseId)
                .param("cutoff",utc(asOf.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Riyadh")).toInstant()))
                .query((rs,row)->{
                    BigDecimal contractual=rs.getBigDecimal("amount");
                    BigDecimal net=rs.getBigDecimal("net_evidenced");
                    BigDecimal gap=contractual.subtract(net);
                    LocalDate due=rs.getObject("due_on",LocalDate.class);
                    String state=gap.signum()==0 ? "DOCUMENTARY_COVERED"
                            : due.isBefore(asOf) ? "OVERDUE_DOCUMENTARY_GAP"
                            : net.signum()>0 ? "PARTIALLY_EVIDENCED"
                            : "NOT_YET_DUE_OR_DUE_TODAY";
                    return new RentEvidencePosition(
                            rs.getObject("id",UUID.class),rs.getObject("lease_id",UUID.class),
                            due,rs.getString("currency"),contractual,net,gap,state);
                }).list();
    }

    PropertyOccupancyInsight occupancyInsight(UUID workspaceId,UUID propertyId,Instant asOf) {
        return jdbc.sql("""
                select
                    count(*) as total_units,
                    count(*) filter (where occ.checked_in_at is not null) as known_units,
                    count(*) filter (
                       where occ.checked_in_at is not null
                         and (occ.checked_out_at is null or occ.checked_out_at > :asOf)
                    ) as occupied_units,
                    count(*) filter (
                       where occ.checked_in_at is not null and occ.checked_out_at <= :asOf
                    ) as vacancy_units
                  from tenancy.unit u
                  left join lateral (
                     select o.checked_in_at,o.checked_out_at
                       from tenancy.occupancy o
                      where o.workspace_id=u.workspace_id and o.unit_id=u.id
                        and o.checked_in_at <= :asOf
                      order by o.checked_in_at desc
                      limit 1
                  ) occ on true
                 where u.workspace_id=:w and u.property_id=:p and u.status='ACTIVE'
                """)
                .param("w",workspaceId).param("p",propertyId).param("asOf",utc(asOf))
                .query((rs,row)->{
                    int total=rs.getInt("total_units");
                    int known=rs.getInt("known_units");
                    int occupied=rs.getInt("occupied_units");
                    int vacant=rs.getInt("vacancy_units");
                    BigDecimal coverage=total==0 ? null
                            : ratio(known,total);
                    BigDecimal occupiedShare=known==0 ? null : ratio(occupied,known);
                    return new PropertyOccupancyInsight(propertyId,asOf,total,known,
                            occupied,vacant,total-known,coverage,occupiedShare,
                            total==0 ? "NO_UNITS" : known==0 ? "NO_EVIDENCE"
                                    : known<total ? "PARTIAL_EVIDENCE" : "COMPLETE_EVIDENCE");
                }).single();
    }

    private BigDecimal ratio(int numerator,int denominator) {
        return BigDecimal.valueOf(numerator).divide(
                BigDecimal.valueOf(denominator),6,java.math.RoundingMode.HALF_UP);
    }

    private RentEvidenceEntry map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RentEvidenceEntry(
                rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
                rs.getObject("lease_id",UUID.class),rs.getObject("installment_id",UUID.class),
                rs.getString("entry_type"),rs.getBigDecimal("amount"),rs.getString("currency"),
                rs.getObject("evidence_id",UUID.class),rs.getString("external_reference"),
                rs.getObject("reverses_entry_id",UUID.class),rs.getString("note"),
                rs.getObject("recorded_by",UUID.class),
                instant(rs.getObject("recorded_at",OffsetDateTime.class)));
    }
    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at,ZoneOffset.UTC);
    }
    private static Instant instant(OffsetDateTime at) {
        return at==null?null:at.toInstant();
    }
}
