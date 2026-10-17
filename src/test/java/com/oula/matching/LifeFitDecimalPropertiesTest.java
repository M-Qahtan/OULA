package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.intent.IntentStatus;
import com.oula.property.PropertyCandidate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifeFitDecimalPropertiesTest {
  private final UUID workspace = UUID.randomUUID();
  private final LifeFitCalculator calculator = new LifeFitCalculator();
  private Intent intent(BigDecimal budget) {
    return new Intent(UUID.randomUUID(), workspace, budget, 2, Set.of("Riyadh"), IntentStatus.ACTIVE);
  }
  private PropertyCandidate candidate(BigDecimal price, int commute, int verified, int total) {
    return new PropertyCandidate(UUID.randomUUID(), workspace, price, 2, "Riyadh", commute, verified, total);
  }

  @Test void financialHalfCentBoundaryDoesNotRoundUtilizationEarly() {
    var i = intent(new BigDecimal("100000"));
    assertThat(calculator.score(i, candidate(new BigDecimal("85005.00"), 15, 1, 1)).dimensions().get("financial")).isEqualTo(100.0);
    assertThat(calculator.score(i, candidate(new BigDecimal("85005.01"), 15, 1, 1)).dimensions().get("financial")).isEqualTo(99.99);
    // Weighted final score must also use the unrounded dimension.
    assertThat(calculator.score(i, candidate(new BigDecimal("85016.67"), 15, 1, 1)).score()).isEqualTo(99.99);
  }

  @Test void finiteResultsAcrossExtremePositiveAmountsAndCommutes() {
    for (String amount : new String[]{"1E-1000", "1", "1E+1000"}) {
      var i = intent(new BigDecimal(amount));
      var result = calculator.score(i, candidate(new BigDecimal(amount), Integer.MAX_VALUE, 0, 0));
      assertThat(result.score()).isEqualTo(65.5);
      assertThat(result.dimensions().get("financial")).isEqualTo(85.0);
      assertThat(result.dimensions().get("mobility")).isZero();
      assertThat(result.confidence()).isEqualTo(.55);
    }
  }

  @Test void seededMetamorphicCorpusPreservesScaleMonotonicityAndBounds() {
    Random random = new Random(113L);
    for (int trial = 0; trial < 1000; trial++) {
      BigDecimal budget = BigDecimal.valueOf(10000L + random.nextInt(10000000));
      BigDecimal price = budget.multiply(BigDecimal.valueOf(1L + random.nextInt(15000), 4));
      int commute = random.nextInt(200);
      int total = 1 + random.nextInt(100);
      int verified = random.nextInt(total + 1);
      var i = intent(budget);
      var result = calculator.score(i, candidate(price, commute, verified, total));
      BigDecimal scale = BigDecimal.TEN.pow(random.nextInt(20));
      var scaled = calculator.score(intent(budget.multiply(scale)), candidate(price.multiply(scale), commute, verified, total));
      assertThat(scaled).isEqualTo(result);
      assertThat(result.score()).isBetween(0.0, 100.0);
      assertThat(result.confidence()).isBetween(.55, 1.0);
      assertThat(calculator.score(i, candidate(price.add(budget.movePointLeft(4)), commute, verified, total)).score()).isLessThanOrEqualTo(result.score());
      assertThat(calculator.score(i, candidate(price, commute + 1, verified, total)).score()).isLessThanOrEqualTo(result.score());
      var fuller = calculator.score(i, candidate(price, commute, total, total));
      assertThat(fuller.score()).isGreaterThanOrEqualTo(result.score());
      assertThat(fuller.confidence()).isGreaterThanOrEqualTo(result.confidence());
    }
  }

  @Test void directScoringRejectsCrossWorkspaceAndNullInputs() {
    var i = intent(BigDecimal.TEN);
    var foreign = new PropertyCandidate(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 2, "Riyadh", 0, 0, 0);
    assertThatThrownBy(() -> calculator.score(i, foreign)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> calculator.score(null, foreign)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> calculator.score(i, null)).isInstanceOf(NullPointerException.class);
  }
}
