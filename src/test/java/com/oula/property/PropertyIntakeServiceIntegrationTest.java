package com.oula.property;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PropertyIntakeServiceIntegrationTest {
    @Autowired PropertyIntakeService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void declaredDemoPropertyHasSeparateDraftListing() {
        UUID workspace=UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id,workspace_type,name,status) values (?,'PERSONAL','Intake Test','ACTIVE')",workspace);
        AccessContext access=new AccessContext(UUID.randomUUID(),"demo",workspace,
                AccessPurpose.PROPERTY_DECISION_SUPPORT);
        UUID propertyId=UUID.randomUUID();
        var property=service.createProperty(access,propertyId,"RESIDENTIAL","Al Malqa",
                3,new BigDecimal("1250000"),true);
        assertThat(property.dataOrigin()).isEqualTo("DEMO");
        assertThat(property.truthStatus()).isEqualTo("DECLARED");
        var listing=service.createListing(access,UUID.randomUUID(),propertyId,"SALE",
                new BigDecimal("1250000"));
        assertThat(listing.status()).isEqualTo("DRAFT");
        assertThat(listing.propertyId()).isEqualTo(propertyId);
        assertThat(service.searchListings(access,"Al Malqa")).hasSize(1);
        // Audit and outbox must share the same transaction as the private writes.
        assertThat(jdbc.queryForObject(
                "select count(*) from platform.audit_log where workspace_id = ? and action in ('PROPERTY_INTAKE_CREATED', 'LISTING_DRAFT_CREATED')",
                Long.class, workspace)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select count(*) from platform.outbox_event where workspace_id = ? and event_type in ('property.asset.declared.v1', 'market.listing.drafted.v1')",
                Long.class, workspace)).isEqualTo(2L);
    }
}
