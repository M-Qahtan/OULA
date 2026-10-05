package com.oula.matching;
import com.oula.intent.Intent;
import com.oula.intent.IntentStatus;
import com.oula.property.PropertyCandidate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
class LifeFitCalculatorTest {
  @Test void producesExplainableScoreAndConfidence() {
    UUID workspace = UUID.randomUUID();
    Intent intent = new Intent(UUID.randomUUID(), workspace, new BigDecimal("2000000"), 4, Set.of("Al Yasmin"), IntentStatus.ACTIVE);
    PropertyCandidate property = new PropertyCandidate(UUID.randomUUID(), workspace, new BigDecimal("1800000"), 4, "Al Yasmin", 22, 18, 20);
    LifeFitResult result = new LifeFitCalculator().score(intent, property);
    assertThat(result.score()).isBetween(0.0, 100.0);
    assertThat(result.confidence()).isEqualTo(0.96);
    assertThat(result.dimensions()).containsKeys("financial", "location", "household", "mobility", "truth");
    assertThat(result.dimensions().get("truth")).isEqualTo(90.0);
  }
}
