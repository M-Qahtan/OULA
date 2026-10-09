package com.oula.database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CI_DB_TEST", matches = "true")
class MigrationSmokeTest {
  @Autowired JdbcTemplate jdbc;
  @Test void migrationsCreatePostgisAndGoldenPathTables() {
    String postgis = jdbc.queryForObject("select PostGIS_Version()", String.class);
    String matchRun = jdbc.queryForObject("select to_regclass('matching.match_run')::text", String.class);
    String outbox = jdbc.queryForObject("select to_regclass('platform.outbox_event')::text", String.class);
    assertThat(postgis).isNotBlank();
    assertThat(matchRun).isEqualTo("matching.match_run");
    assertThat(outbox).isEqualTo("platform.outbox_event");\n    assertThat(vitals).isEqualTo("vitals.property_vital_snapshot");
  }
}
