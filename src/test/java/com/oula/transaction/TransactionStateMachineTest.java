package com.oula.transaction;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class TransactionStateMachineTest {
  private final TransactionStateMachine machine = new TransactionStateMachine();
  @Test void allowsGoldenPathTransitions() {
    assertThat(machine.transition(TransactionStage.DRAFT, TransactionStage.QUALIFIED)).isEqualTo(TransactionStage.QUALIFIED);
    assertThat(machine.transition(TransactionStage.QUALIFIED, TransactionStage.VIEWING)).isEqualTo(TransactionStage.VIEWING);
    assertThat(machine.transition(TransactionStage.HANDOVER, TransactionStage.COMPLETED)).isEqualTo(TransactionStage.COMPLETED);
  }
  @Test void rejectsIllegalJump() {
    assertThatThrownBy(() -> machine.transition(TransactionStage.DRAFT, TransactionStage.COMPLETED))
      .isInstanceOf(IllegalStateException.class).hasMessageContaining("DRAFT -> COMPLETED");
  }
}
