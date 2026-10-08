package com.oula.integration;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IntegrationTrustFabricIntegrationTest {
    @Autowired IntegrationTrustService integrations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void acceptsOnlyVerifiedContractBoundReplaySafeInboundMetadata() {
        Fixture fixture = fixture(true, false);

        IntegrationContract contract = integrations.createContract(
                fixture.access(),
                fixture.partner().id(),
                new CreateIntegrationContractCommand(
                        "PROPERTY_STATUS_IN",
                        "1",
                        "INBOUND",
                        "PROPERTY_STATUS_SYNC",
                        "PROPERTY_STATUS_UPDATED",
                        "PROPERTY",
                        Set.of("PROPERTY_ID", "STATUS"),
                        null,
                        null
                ),
                UUID.randomUUID()
        );

        VerifiedExternalPrincipal unverifiedPrincipal = principal(fixture.partner());

        InboundIntegrationCommand command = new InboundIntegrationCommand(
                "evt-001",
                "PROPERTY_STATUS_UPDATED",
                "PROPERTY",
                "PROPERTY_STATUS_SYNC",
                Set.of("PROPERTY_ID", "STATUS"),
                "sha256:abc123",
                "s3://evidence/integration/evt-001",
                UUID.randomUUID()
        );

        assertThatThrownBy(() ->
                integrations.accept(unverifiedPrincipal, command)
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("not eligible");

        IntegrationPartner verified = integrations.verifyPartner(
                fixture.access(),
                fixture.partner().id(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        VerifiedExternalPrincipal principal = principal(verified);
        InboundIntegrationReceipt receipt = integrations.accept(principal, command);

        assertThat(receipt.status()).isEqualTo("ACCEPTED");
        assertThat(receipt.contractId()).isEqualTo(contract.id());
        assertThat(receipt.dataClasses())
                .containsExactlyInAnyOrder("PROPERTY_ID", "STATUS");

        InboundIntegrationReceipt replay = integrations.accept(principal, command);
        assertThat(replay.id()).isEqualTo(receipt.id());
        assertThat(count("integration.inbound_receipt")).isEqualTo(1);

        InboundIntegrationCommand changedReplay = new InboundIntegrationCommand(
                command.externalEventId(),
                command.operation(),
                command.resourceType(),
                command.purpose(),
                command.dataClasses(),
                "sha256:different",
                command.payloadReference(),
                UUID.randomUUID()
        );
        assertThatThrownBy(() -> integrations.accept(principal, changedReplay))
                .isInstanceOf(IntegrationReplayConflictException.class)
                .hasMessageContaining("replayed");

        InboundIntegrationCommand excessiveData = new InboundIntegrationCommand(
                "evt-002",
                command.operation(),
                command.resourceType(),
                command.purpose(),
                Set.of("PROPERTY_ID", "STATUS", "OWNER_IDENTITY"),
                "sha256:def456",
                "s3://evidence/integration/evt-002",
                UUID.randomUUID()
        );
        assertThatThrownBy(() -> integrations.accept(principal, excessiveData))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not allow");

        Integer rawPayloadColumns = jdbc.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'integration'
                   and table_name = 'inbound_receipt'
                   and column_name in ('payload','raw_payload','body')
                """, Integer.class);
        assertThat(rawPayloadColumns).isZero();
    }

    @Test
    void preparesOutboundOnlyForVerifiedPartnerAndContractedDataSubset() {
        Fixture fixture = fixture(false, true);
        IntegrationPartner verified = integrations.verifyPartner(
                fixture.access(),
                fixture.partner().id(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        integrations.createContract(
                fixture.access(),
                verified.id(),
                new CreateIntegrationContractCommand(
                        "REGISTRY_VERIFY_OUT",
                        "1",
                        "OUTBOUND",
                        "PROPERTY_VERIFICATION",
                        "VERIFY_PROPERTY",
                        "PROPERTY",
                        Set.of("PROPERTY_ID", "PARCEL_ID"),
                        null,
                        null
                ),
                UUID.randomUUID()
        );

        UUID propertyId = UUID.randomUUID();
        OutboundIntegrationRequest prepared = integrations.prepareOutbound(
                fixture.access(),
                verified.id(),
                new PrepareOutboundIntegrationCommand(
                        "VERIFY_PROPERTY",
                        "PROPERTY",
                        propertyId,
                        "PROPERTY_VERIFICATION",
                        Set.of("PROPERTY_ID"),
                        "sha256:outbound001",
                        "s3://outbound/verify-property-001",
                        UUID.randomUUID()
                )
        );

        assertThat(prepared.status()).isEqualTo("PREPARED");
        assertThat(prepared.resourceId()).isEqualTo(propertyId);
        assertThat(count("integration.outbound_request")).isEqualTo(1);

        assertThatThrownBy(() -> integrations.prepareOutbound(
                fixture.access(),
                verified.id(),
                new PrepareOutboundIntegrationCommand(
                        "VERIFY_PROPERTY",
                        "PROPERTY",
                        propertyId,
                        "PROPERTY_VERIFICATION",
                        Set.of("PROPERTY_ID", "OWNER_IDENTITY"),
                        "sha256:outbound002",
                        "s3://outbound/verify-property-002",
                        UUID.randomUUID()
                )
        )).isInstanceOf(SecurityException.class)
          .hasMessageContaining("does not allow");
    }

    private Fixture fixture(boolean inbound, boolean outbound) {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seed(workspaceId);

        AccessContext access = new AccessContext(
                actorId,
                "integration-admin",
                workspaceId,
                AccessPurpose.INTEGRATION_OPERATIONS
        );

        IntegrationPartner partner = integrations.registerPartner(
                access,
                new CreateIntegrationPartnerCommand(
                        "PARTNER_" + UUID.randomUUID().toString().substring(0, 8),
                        "REGISTRY",
                        "Trusted Registry",
                        "SA",
                        "JWS",
                        "secrets://integration/registry/key-01",
                        inbound,
                        outbound
                ),
                UUID.randomUUID()
        );
        return new Fixture(access, partner);
    }

    private VerifiedExternalPrincipal principal(IntegrationPartner partner) {
        return new VerifiedExternalPrincipal(
                partner.workspaceId(),
                partner.id(),
                partner.partnerCode(),
                partner.authMode(),
                partner.credentialReference(),
                Instant.now()
        );
    }

    private void seed(UUID workspaceId) {
        jdbc.update(
                "insert into iam.workspace(id, workspace_type, name, status) values (?,?,?,?)",
                workspaceId, "PERSONAL", "Integration Test", "ACTIVE"
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table, Long.class
        );
        return value == null ? 0 : value;
    }

    private record Fixture(
            AccessContext access,
            IntegrationPartner partner
    ) {}
}
