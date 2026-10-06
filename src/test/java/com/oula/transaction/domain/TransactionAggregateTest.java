package com.oula.transaction.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionAggregateTest {
    @Test
    void blocksIllegalStageJump() {
        var tx = new TransactionAggregate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> tx.transitionTo(TransactionStage.CLOSING));
    }

    @Test
    void completesOnlyThroughValidPath() {
        var tx = new TransactionAggregate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        tx.transitionTo(TransactionStage.QUALIFIED);
        tx.transitionTo(TransactionStage.VIEWING);
        tx.transitionTo(TransactionStage.OFFERING);
        tx.transitionTo(TransactionStage.DUE_DILIGENCE);
        tx.transitionTo(TransactionStage.CONTRACTING);
        tx.transitionTo(TransactionStage.CLOSING);
        tx.transitionTo(TransactionStage.HANDOVER);
        tx.transitionTo(TransactionStage.COMPLETED);
        assertEquals(TransactionStatus.COMPLETED, tx.status());
    }
}
