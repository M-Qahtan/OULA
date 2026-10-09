package com.oula.leasing;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
class LeasingRepository {
    private final JdbcClient jdbc;

    LeasingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Lease lease) {
        jdbc.sql("""
                insert into leasing.lease
                    (id, workspace_id, property_id, landlord_party_id, tenant_party_id,
                     lease_type, status, starts_at, ends_at, rent_amount, currency,
                     payment_frequency, security_deposit, external_contract_reference,
                     source_type, created_by, created_at, version)
                values
                    (:id, :workspaceId, :propertyId, :landlordPartyId, :tenantPartyId,
                     :leaseType, :status, :startsAt, :endsAt, :rentAmount, :currency,
                     :paymentFrequency, :securityDeposit, :externalContractReference,
                     :sourceType, :createdBy, :createdAt, :version)
                """)
                .param("id", lease.id())
                .param("workspaceId", lease.workspaceId())
                .param("propertyId", lease.propertyId())
                .param("landlordPartyId", lease.landlordPartyId())
                .param("tenantPartyId", lease.tenantPartyId())
                .param("leaseType", lease.leaseType())
                .param("status", lease.status())
                .param("startsAt", utc(lease.startsAt()))
                .param("endsAt", utc(lease.endsAt()))
                .param("rentAmount", lease.rentAmount())
                .param("currency", lease.currency())
                .param("paymentFrequency", lease.paymentFrequency())
                .param("securityDeposit", lease.securityDeposit())
                .param("externalContractReference", lease.externalContractReference())
                .param("sourceType", lease.sourceType())
                .param("createdBy", lease.createdBy())
                .param("createdAt", utc(lease.createdAt()))
                .param("version", lease.version())
                .update();
    }

    Lease lock(UUID workspaceId, UUID leaseId) {
        return jdbc.sql("""
                select *
                  from leasing.lease
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", leaseId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapLease(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("lease not found"));
    }

    boolean evidenceVerified(UUID workspaceId, UUID evidenceId) {
        Integer count = jdbc.sql("""
                select count(*)
                  from docs.evidence
                 where id = :evidenceId
                   and workspace_id = :workspaceId
                   and verification_status = 'VERIFIED'
                """)
                .param("evidenceId", evidenceId)
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
        return count != null && count == 1;
    }

    Lease activate(
            Lease current,
            UUID evidenceId,
            UUID actorId,
            Instant activatedAt
    ) {
        int updated = jdbc.sql("""
                update leasing.lease
                   set status = 'ACTIVE',
                       contract_evidence_id = :evidenceId,
                       activated_by = :actorId,
                       activated_at = :activatedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'DRAFT'
                """)
                .param("evidenceId", evidenceId)
                .param("actorId", actorId)
                .param("activatedAt", utc(activatedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update();
        requireUpdated(updated);
        return new Lease(
                current.id(), current.workspaceId(), current.propertyId(),
                current.landlordPartyId(), current.tenantPartyId(), current.leaseType(),
                "ACTIVE", current.startsAt(), current.endsAt(), current.rentAmount(),
                current.currency(), current.paymentFrequency(), current.securityDeposit(),
                evidenceId, current.externalContractReference(), current.sourceType(),
                current.createdBy(), current.createdAt(), actorId, activatedAt,
                null, null, null, current.version() + 1
        );
    }

    OccupancyPeriod insertOccupancy(Lease lease, Instant createdAt) {
        OccupancyPeriod occupancy = new OccupancyPeriod(
                com.oula.platform.UuidV7.next(), lease.workspaceId(), lease.propertyId(),
                lease.id(), lease.tenantPartyId(), "OPEN", lease.startsAt(), lease.endsAt(),
                createdAt, null, 0
        );
        try {
            jdbc.sql("""
                    insert into leasing.occupancy_period
                        (id, workspace_id, property_id, lease_id, occupant_party_id,
                         status, starts_at, ends_at, created_at, version)
                    values
                        (:id, :workspaceId, :propertyId, :leaseId, :occupantPartyId,
                         'OPEN', :startsAt, :endsAt, :createdAt, 0)
                    """)
                    .param("id", occupancy.id())
                    .param("workspaceId", occupancy.workspaceId())
                    .param("propertyId", occupancy.propertyId())
                    .param("leaseId", occupancy.leaseId())
                    .param("occupantPartyId", occupancy.occupantPartyId())
                    .param("startsAt", utc(occupancy.startsAt()))
                    .param("endsAt", utc(occupancy.endsAt()))
                    .param("createdAt", utc(occupancy.createdAt()))
                    .update();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "property already has overlapping open occupancy", ex
            );
        }
        return occupancy;
    }

    Lease terminate(
            Lease current,
            UUID actorId,
            Instant terminatedAt,
            String reason
    ) {
        int updated = jdbc.sql("""
                update leasing.lease
                   set status = 'TERMINATED',
                       terminated_by = :actorId,
                       terminated_at = :terminatedAt,
                       termination_reason = :reason,
                       version = version + 1
                 where id = :id
                   and version = :version
                   and status = 'ACTIVE'
                """)
                .param("actorId", actorId)
                .param("terminatedAt", utc(terminatedAt))
                .param("reason", reason)
                .param("id", current.id())
                .param("version", current.version())
                .update();
        requireUpdated(updated);
        return new Lease(
                current.id(), current.workspaceId(), current.propertyId(),
                current.landlordPartyId(), current.tenantPartyId(), current.leaseType(),
                "TERMINATED", current.startsAt(), current.endsAt(), current.rentAmount(),
                current.currency(), current.paymentFrequency(), current.securityDeposit(),
                current.contractEvidenceId(), current.externalContractReference(),
                current.sourceType(), current.createdBy(), current.createdAt(),
                current.activatedBy(), current.activatedAt(), actorId, terminatedAt,
                reason, current.version() + 1
        );
    }

    void closeOccupancy(UUID leaseId, Instant closedAt) {
        int updated = jdbc.sql("""
                update leasing.occupancy_period
                   set status = 'CLOSED',
                       ends_at = least(ends_at, :closedAt),
                       closed_at = :closedAt,
                       version = version + 1
                 where lease_id = :leaseId
                   and status = 'OPEN'
                """)
                .param("closedAt", utc(closedAt))
                .param("leaseId", leaseId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("active lease occupancy is missing or already closed");
        }
    }

    List<Lease> list(UUID workspaceId, UUID propertyId) {
        return jdbc.sql("""
                select *
                  from leasing.lease
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                 order by starts_at desc, created_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .query((rs, rowNum) -> mapLease(rs))
                .list();
    }

    OccupancyPeriod currentOccupancy(
            UUID workspaceId,
            UUID propertyId,
            Instant now
    ) {
        return jdbc.sql("""
                select id, workspace_id, property_id, lease_id, occupant_party_id,
                       status, starts_at, ends_at, created_at, closed_at, version
                  from leasing.occupancy_period
                 where workspace_id = :workspaceId
                   and property_id = :propertyId
                   and status = 'OPEN'
                   and starts_at <= :now
                   and ends_at > :now
                 order by starts_at desc
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("propertyId", propertyId)
                .param("now", utc(now))
                .query((rs, rowNum) -> mapOccupancy(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("current occupancy not found"));
    }

    private Lease mapLease(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Lease(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("landlord_party_id", UUID.class),
                rs.getObject("tenant_party_id", UUID.class),
                rs.getString("lease_type"),
                rs.getString("status"),
                instant(rs.getObject("starts_at", OffsetDateTime.class)),
                instant(rs.getObject("ends_at", OffsetDateTime.class)),
                rs.getBigDecimal("rent_amount"),
                rs.getString("currency"),
                rs.getString("payment_frequency"),
                rs.getBigDecimal("security_deposit"),
                rs.getObject("contract_evidence_id", UUID.class),
                rs.getString("external_contract_reference"),
                rs.getString("source_type"),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                rs.getObject("activated_by", UUID.class),
                instant(rs.getObject("activated_at", OffsetDateTime.class)),
                rs.getObject("terminated_by", UUID.class),
                instant(rs.getObject("terminated_at", OffsetDateTime.class)),
                rs.getString("termination_reason"),
                rs.getLong("version")
        );
    }

    private OccupancyPeriod mapOccupancy(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OccupancyPeriod(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("property_id", UUID.class),
                rs.getObject("lease_id", UUID.class),
                rs.getObject("occupant_party_id", UUID.class),
                rs.getString("status"),
                instant(rs.getObject("starts_at", OffsetDateTime.class)),
                instant(rs.getObject("ends_at", OffsetDateTime.class)),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("closed_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("lease state changed concurrently");
        }
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
