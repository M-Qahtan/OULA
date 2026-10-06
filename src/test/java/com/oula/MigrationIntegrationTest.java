package com.oula;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class MigrationIntegrationTest {

    private static final DockerImageName POSTGIS =
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGIS)
            .withDatabaseName("oula")
            .withUsername("oula")
            .withPassword("oula");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void applicationStartsWithAllFlywayMigrationsApplied() {
        // Context startup is the assertion: Flyway and PostGIS must initialize successfully.
    }
}
