package com.oula.intent.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntentTest {
    @Test
    void rejectsInvertedBudget() {
        assertThrows(IllegalArgumentException.class, () -> new Intent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), IntentType.BUY,
                Currency.getInstance("SAR"), new BigDecimal("2000000"), new BigDecimal("1000000"), null, null));
    }

    @Test
    void activatesDraftIntent() {
        var intent = new Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), IntentType.BUY,
                Currency.getInstance("SAR"), null, null, null, null);
        intent.activate();
        assertEquals(IntentStatus.ACTIVE, intent.status());
    }
}
