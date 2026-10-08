package com.oula.integration;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Repository
class IntegrationTrustRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    IntegrationTrustRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insertPartner(IntegrationPartner partner) {
        jdbc.sql("""
                insert into integration.partner
                    (id, workspace_id, partner_code, partner_type, display_name,
                     jurisdiction, status, verification_status, auth_mode,
                     credential_reference, inbound_enabled, outbound_enabled,
                     created_by, created_at, version)
                values
                    (:id, :workspaceId, :partnerCode, :partnerType, :displayName,
                     :jurisdiction, :status, :verificationStatus, :authMode,
                     :credentialReference, :inboundEnabled, :outboundEnabled,
                     :createdBy, :createdAt, :version)
                """)
                .param("id", partner.id())
                .param("workspaceId", partner.workspaceId())
                .param("partnerCode", partner.partnerCode())
                .param("partnerType", partner.partnerType())
                .param("displayName", partner.displayName())
                .param("jurisdiction", partner.jurisdiction())
                .param("status", partner.status())
                .param("verificationStatus", partner.verificationStatus())
                .param("authMode", partner.authMode())
                .param("credentialReference", partner.credentialReference())
                .param("inboundEnabled", partner.inboundEnabled())
                .param("outboundEnabled", partner.outboundEnabled())
                .param("createdBy", partner.createdBy())
                .param("createdAt", utc(partner.createdAt()))
                .param("version", partner.version())
                .update();
    }

    IntegrationPartner lockPartner(UUID workspaceId, UUID partnerId) {
        return jdbc.sql("""
                select *
                  from integration.partner
                 where id = :id
                   and workspace_id = :workspaceId
                 for update
                """)
                .param("id", partnerId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapPartner(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("integration partner not found"));
    }

    IntegrationPartner partner(UUID workspaceId, UUID partnerId) {
        return jdbc.sql("""
                select *
                  from integration.partner
                 where id = :id
                   and workspace_id = :workspaceId
                """)
                .param("id", partnerId)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> mapPartner(rs))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("integration partner not found"));
    }

    IntegrationPartner verifyPartner(
            IntegrationPartner current,
            UUID evidenceId,
            Instant verifiedAt
    ) {
        int updated = jdbc.sql("""
                update integration.partner
                   set verification_status = 'VERIFIED',
                       verification_evidence_id = :evidenceId,
                       verified_at = :verifiedAt,
                       version = version + 1
                 where id = :id
                   and version = :version
                """)
                .param("evidenceId", evidenceId)
                .param("verifiedAt", utc(verifiedAt))
                .param("id", current.id())
                .param("version", current.version())
                .update();
        requireUpdated(updated);
        return new IntegrationPartner(
                current.id(), current.workspaceId(), current.partnerCode(),
                current.partnerType(), current.displayName(), current.jurisdiction(),
                current.status(), "VERIFIED", evidenceId, verifiedAt,
                current.authMode(), current.credentialReference(),
                current.inboundEnabled(), current.outboundEnabled(),
                current.createdBy(), current.createdAt(), current.version() + 1
        );
    }

    void insertContract(IntegrationContract contract) {
        jdbc.sql("""
                insert into integration.contract
                    (id, workspace_id, partner_id, contract_key, version,
                     direction, purpose, operation, resource_type,
                     allowed_data_classes, status, effective_from,
                     effective_until, created_by, created_at)
                values
                    (:id, :workspaceId, :partnerId, :contractKey, :version,
                     :direction, :purpose, :operation, :resourceType,
                     cast(:dataClasses as jsonb), :status, :effectiveFrom,
                     :effectiveUntil, :createdBy, :createdAt)
                """)
                .param("id", contract.id())
                .param("workspaceId", contract.workspaceId())
                .param("partnerId", contract.partnerId())
                .param("contractKey", contract.contractKey())
                .param("version", contract.version())
                .param("direction", contract.direction())
                .param("purpose", contract.purpose())
                .param("operation", contract.operation())
                .param("resourceType", contract.resourceType())
                .param("dataClasses", write(contract.allowedDataClasses()))
                .param("status", contract.status())
                .param("effectiveFrom", utc(contract.effectiveFrom()))
                .param("effectiveUntil", utc(contract.effectiveUntil()))
                .param("createdBy", contract.createdBy())
                .param("createdAt", utc(contract.createdAt()))
                .update();
    }

    IntegrationContract matchingContract(
            UUID workspaceId,
            UUID partnerId,
            String direction,
            String purpose,
            String operation,
            String resourceType,
            Instant now
    ) {
        return jdbc.sql("""
                select *
                  from integration.contract
                 where workspace_id = :workspaceId
                   and partner_id = :partnerId
                   and direction = :direction
                   and purpose = :purpose
                   and operation = :operation
                   and resource_type = :resourceType
                   and status = 'ACTIVE'
                   and effective_from <= :now
                   and (effective_until is null or effective_until > :now)
                 order by effective_from desc, created_at desc, id
                 limit 1
                """)
                .param("workspaceId", workspaceId)
                .param("partnerId", partnerId)
                .param("direction", direction)
                .param("purpose", purpose)
                .param("operation", operation)
                .param("resourceType", resourceType)
                .param("now", utc(now))
                .query((rs, rowNum) -> mapContract(rs))
                .optional()
                .orElse(null);
    }

    InboundIntegrationReceipt inboundReceipt(UUID partnerId, String externalEventId) {
        return jdbc.sql("""
                select *
                  from integration.inbound_receipt
                 where partner_id = :partnerId
                   and external_event_id = :externalEventId
                """)
                .param("partnerId", partnerId)
                .param("externalEventId", externalEventId)
                .query((rs, rowNum) -> mapInbound(rs))
                .optional()
                .orElse(null);
    }

    void insertInbound(InboundIntegrationReceipt receipt) {
        jdbc.sql("""
                insert into integration.inbound_receipt
                    (id, workspace_id, partner_id, contract_id, external_event_id,
                     operation, resource_type, purpose, data_classes,
                     payload_hash, payload_reference, auth_mode,
                     credential_reference, authenticated_at, received_at,
                     correlation_id, status)
                values
                    (:id, :workspaceId, :partnerId, :contractId, :externalEventId,
                     :operation, :resourceType, :purpose, cast(:dataClasses as jsonb),
                     :payloadHash, :payloadReference, :authMode,
                     :credentialReference, :authenticatedAt, :receivedAt,
                     :correlationId, :status)
                """)
                .param("id", receipt.id())
                .param("workspaceId", receipt.workspaceId())
                .param("partnerId", receipt.partnerId())
                .param("contractId", receipt.contractId())
                .param("externalEventId", receipt.externalEventId())
                .param("operation", receipt.operation())
                .param("resourceType", receipt.resourceType())
                .param("purpose", receipt.purpose())
                .param("dataClasses", write(receipt.dataClasses()))
                .param("payloadHash", receipt.payloadHash())
                .param("payloadReference", receipt.payloadReference())
                .param("authMode", receipt.authMode())
                .param("credentialReference", receipt.credentialReference())
                .param("authenticatedAt", utc(receipt.authenticatedAt()))
                .param("receivedAt", utc(receipt.receivedAt()))
                .param("correlationId", receipt.correlationId())
                .param("status", receipt.status())
                .update();
    }

    void insertOutbound(OutboundIntegrationRequest request) {
        jdbc.sql("""
                insert into integration.outbound_request
                    (id, workspace_id, partner_id, contract_id, operation,
                     resource_type, resource_id, purpose, data_classes,
                     payload_hash, payload_reference, status, created_by,
                     created_at, correlation_id)
                values
                    (:id, :workspaceId, :partnerId, :contractId, :operation,
                     :resourceType, :resourceId, :purpose,
                     cast(:dataClasses as jsonb), :payloadHash,
                     :payloadReference, :status, :createdBy,
                     :createdAt, :correlationId)
                """)
                .param("id", request.id())
                .param("workspaceId", request.workspaceId())
                .param("partnerId", request.partnerId())
                .param("contractId", request.contractId())
                .param("operation", request.operation())
                .param("resourceType", request.resourceType())
                .param("resourceId", request.resourceId())
                .param("purpose", request.purpose())
                .param("dataClasses", write(request.dataClasses()))
                .param("payloadHash", request.payloadHash())
                .param("payloadReference", request.payloadReference())
                .param("status", request.status())
                .param("createdBy", request.createdBy())
                .param("createdAt", utc(request.createdAt()))
                .param("correlationId", request.correlationId())
                .update();
    }

    private IntegrationPartner mapPartner(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IntegrationPartner(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("partner_code"),
                rs.getString("partner_type"),
                rs.getString("display_name"),
                rs.getString("jurisdiction"),
                rs.getString("status"),
                rs.getString("verification_status"),
                rs.getObject("verification_evidence_id", UUID.class),
                instant(rs.getObject("verified_at", OffsetDateTime.class)),
                rs.getString("auth_mode"),
                rs.getString("credential_reference"),
                rs.getBoolean("inbound_enabled"),
                rs.getBoolean("outbound_enabled"),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                rs.getLong("version")
        );
    }

    private IntegrationContract mapContract(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IntegrationContract(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("partner_id", UUID.class),
                rs.getString("contract_key"),
                rs.getString("version"),
                rs.getString("direction"),
                rs.getString("purpose"),
                rs.getString("operation"),
                rs.getString("resource_type"),
                readSet(rs.getString("allowed_data_classes")),
                rs.getString("status"),
                instant(rs.getObject("effective_from", OffsetDateTime.class)),
                instant(rs.getObject("effective_until", OffsetDateTime.class)),
                rs.getObject("created_by", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class))
        );
    }

    private InboundIntegrationReceipt mapInbound(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new InboundIntegrationReceipt(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("partner_id", UUID.class),
                rs.getObject("contract_id", UUID.class),
                rs.getString("external_event_id"),
                rs.getString("operation"),
                rs.getString("resource_type"),
                rs.getString("purpose"),
                readSet(rs.getString("data_classes")),
                rs.getString("payload_hash"),
                rs.getString("payload_reference"),
                rs.getString("auth_mode"),
                rs.getString("credential_reference"),
                instant(rs.getObject("authenticated_at", OffsetDateTime.class)),
                instant(rs.getObject("received_at", OffsetDateTime.class)),
                rs.getObject("correlation_id", UUID.class),
                rs.getString("status")
        );
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize integration metadata", ex);
        }
    }

    private Set<String> readSet(String value) {
        try {
            String[] values = json.readValue(value, String[].class);
            return Set.copyOf(Arrays.asList(values));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize integration metadata", ex);
        }
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("integration partner changed concurrently");
        }
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
