package com.oula.platform;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicUuidTest {

    @Test
    void isStableDomainSeparatedAndUsesUuidVersionEight() {
        UUID first = DeterministicUuid.from("matching.run", "workspace-1", "key-1");
        UUID second = DeterministicUuid.from("matching.run", "workspace-1", "key-1");
        UUID other = DeterministicUuid.from("transaction.advance", "workspace-1", "key-1");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
        assertThat(first.version()).isEqualTo(8);
        assertThat(first.variant()).isEqualTo(2);
    }
}
