package com.oula.settlement;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
class SettlementRepository {
    private final JdbcClient jdbc;

    SettlementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    SettlementReference findByExternalReference(String processorCode, String externalReference) {
        return jdbc.sql("""
                select *
                  from settlement.reference
                 where processor_code = :processorCode
                   and external_reference = :externalReference
                """)
                .param("processorCode", processorCode)
                .param("externalReference", externalReference)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElse(null);
    }

    BigDecimal settledAmount(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select coalesce(sum(amount), 0)
                  from settlement.reference
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                   and status = 'SETTLED'
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .query(BigDecimal.class)
                .single();
    }

    void insert(SettlementReference reference) {
        jdbc.sql("""
                insert into settlement.reference
                    (id, workspace_id, work_order_id, provider_party_id,
                     processor_code, external_reference, amount, currency,
                     status, evidence_id, recorded_by, recorded_at)
                values
                    (:id, :workspaceId, :workOrderId, :providerPartyId,
                     :processorCode, :externalReference, :amount, :currency,
                     :status, :evidenceId, :recordedBy, :recordedAt)
                """)
                .param("id", reference.id())
                .param("workspaceId", reference.workspaceId())
                .param("workOrderId", reference.workOrderId())
                .param("providerPartyId", reference.providerPartyId())
                .param("processorCode", reference.processorCode())
                .param("externalReference", reference.externalReference())
                .param("amount", reference.amount())
                .param("currency", reference.currency())
                .param("status", reference.status())
                .param("evidenceId", reference.evidenceId())
                .param("recordedBy", reference.recordedBy())
                .param("recordedAt", utc(reference.recordedAt()))
                .update();
    }

    List<SettlementReference> list(UUID workspaceId, UUID workOrderId) {
        return jdbc.sql("""
                select *
                  from settlement.reference
                 where workspace_id = :workspaceId
                   and work_order_id = :workOrderId
                 order by recorded_at
                """)
                .param("workspaceId", workspaceId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    private SettlementReference map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SettlementReference(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("work_order_id", UUID.class),
                rs.getObject("provider_party_id", UUID.class),
                rs.getString("processor_code"),
                rs.getString("external_reference"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getObject("evidence_id", UUID.class),
                rs.getObject("recorded_by", UUID.class),
                instant(rs.getObject("recorded_at", OffsetDateTime.class))
        );
    }

    private OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
