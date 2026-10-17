package com.oula.iam.dev;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Component
@Profile("dev-auth & !prod")
class DevAuthFixtureCatalog {
    private final JdbcClient jdbc;

    private final List<DevAuthFixture> fixtures = List.of(
            new DevAuthFixture(
                    "buyer",
                    UUID.fromString("10000000-0000-4000-8000-000000000001"),
                    UUID.fromString("20000000-0000-4000-8000-000000000001"),
                    "OULA Dev Buyer",
                    List.of("PROPERTY_DECISION_SUPPORT", "TRANSACTION_EXECUTION"),
                    List.of(
                            "oula.consent.read",
                            "oula.consent.write",
                            "oula.intent.read",
                            "oula.experience.read",
                            "oula.intent.write",
                            "oula.matching.run",
                            "oula.property.passport.read",
                            "oula.intelligence.evidence.write",
                            "oula.intelligence.assumption.write",
                            "oula.intelligence.recommend",
                            "oula.intelligence.read",
                            "oula.intelligence.decide",
                            "oula.intelligence.outcome.write",
                            "oula.transaction.create",
                            "oula.transaction.read",
                            "oula.transaction.viewing.request",
                            "oula.transaction.offer.submit",
                            "oula.transaction.advance"
                    )
            ),
            new DevAuthFixture(
                    "buyer-readonly",
                    UUID.fromString("10000000-0000-4000-8000-000000000003"),
                    UUID.fromString("20000000-0000-4000-8000-000000000001"),
                    "OULA Dev Buyer",
                    List.of("PROPERTY_DECISION_SUPPORT"),
                    List.of("oula.consent.read")
            ),
            new DevAuthFixture(
                    "broker-readonly",
                    UUID.fromString("10000000-0000-4000-8000-000000000004"),
                    UUID.fromString("20000000-0000-4000-8000-000000000004"),
                    "OULA Dev Broker Read-Only Workspace",
                    List.of("TRANSACTION_EXECUTION"),
                    List.of(
                            "oula.experience.read",
                            "oula.transaction.read"
                    )
            ),
            new DevAuthFixture(
                    "property-manager",
                    UUID.fromString("10000000-0000-4000-8000-000000000002"),
                    UUID.fromString("20000000-0000-4000-8000-000000000002"),
                    "OULA Dev Property Manager",
                    List.of("PROPERTY_MANAGEMENT"),
                    List.of(
                            "oula.consent.read",
                            "oula.consent.write",
                            "oula.property.passport.read",
                            "oula.experience.read",
                            "oula.property.write",
                            "oula.property.read",
                            "oula.listing.write",
                            "oula.listing.read",
                            "oula.property.management.read",
                            "oula.property.management.write",
                            "oula.property.guardian.run",
                            "oula.property.vitals.read",
                            "oula.property.vitals.assess",
                            "oula.property.advisory.read"
                    )
            )
    );

    DevAuthFixtureCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void seedWorkspaces() {
        for (DevAuthFixture fixture : fixtures) {
            jdbc.sql("""
                    insert into iam.workspace(id, workspace_type, name, status)
                    values (:id, 'PERSONAL', :name, 'ACTIVE')
                    on conflict (id) do nothing
                    """)
                    .param("id", fixture.workspaceId())
                    .param("name", fixture.workspaceName())
                    .update();
        }
    }

    DevAuthFixture require(String alias) {
        return fixtures.stream()
                .filter(fixture -> fixture.alias().equals(alias))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("unknown dev-auth fixture"));
    }

    List<DevAuthFixture> all() {
        return fixtures;
    }
}
