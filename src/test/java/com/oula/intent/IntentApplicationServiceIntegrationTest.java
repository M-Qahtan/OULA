package com.oula.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CI_DB_TEST", matches = "true")
class IntentApplicationServiceIntegrationTest {
    @Autowired IntentApplicationService intents;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsActiveIntentInExistingDatabaseAndReadsIt() {
        UUID workspaceId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Intent Test', 'ACTIVE')", workspaceId);

        IntentDetails result = intents.create(id, workspaceId, IntentType.BUY,
                new BigDecimal("1500000.00"), 3, Set.of("حطين", "الملقا"));
        IntentDetails reloaded = intents.get(id, workspaceId);

        assertThat(result).isEqualTo(reloaded);
        assertThat(reloaded.status()).isEqualTo(IntentStatus.ACTIVE);
        assertThat(reloaded.preferredDistricts()).containsExactlyInAnyOrder("حطين", "الملقا");
        assertThat(reloaded.budgetMax()).isEqualByComparingTo(new BigDecimal("1500000.00"));
    }

    @Test
    void foreignWorkspaceCannotReadTheIntent() {
        UUID workspaceId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into iam.workspace(id, workspace_type, name, status) values (?, 'PERSONAL', 'Intent Test', 'ACTIVE')", workspaceId);
        intents.create(id, workspaceId, IntentType.RENT, new BigDecimal("120000"), 2, Set.of());

        assertThatThrownBy(() -> intents.get(id, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsInvalidConstraintsBeforeInsertingRow() {
        UUID workspaceId = UUID.randomUUID();
        assertThatThrownBy(() -> intents.create(UUID.randomUUID(), workspaceId,
                IntentType.BUY, BigDecimal.ZERO, 2, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> intents.create(UUID.randomUUID(), workspaceId,
                IntentType.BUY, new BigDecimal("1000"), -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
