package com.oula.matching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifeFitBoundaryTest {
  @Test void rejectsNonFiniteScoresConfidenceAndDimensions() {
    for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 100.01}) {
      assertThatThrownBy(() -> new LifeFitResult(bad, .5, Map.of())).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new LifeFitResult(50, .5, Map.of("truth", bad))).isInstanceOf(IllegalArgumentException.class);
    }
    for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.01, 1.01})
      assertThatThrownBy(() -> new LifeFitResult(50, bad, Map.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test @ResourceLock("default-locale")
  void explanationJsonUsesAsciiDecimalRegardlessOfHostLocale() throws Exception {
    var method = MatchRunRepository.class.getDeclaredMethod("explanationJson", RankedMatch.class);
    method.setAccessible(true);
    var ranked = new RankedMatch(UUID.randomUUID(), 1, 90.25, .75,
        Map.of("financial", 90.25, "location", 100.0, "household", 100.0, "mobility", 88.0, "truth", 75.0));
    Locale original = Locale.getDefault();
    Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-SA")}) {
        Locale.setDefault(locale);
        Locale.setDefault(Locale.Category.FORMAT, locale);
        String json = (String) method.invoke(new MatchRunRepository(null), ranked);
        var parsed = JsonMapper.builder().build().readTree(json);
        assertThat(parsed.size()).isEqualTo(5);
        assertThat(parsed.get("financial").doubleValue()).isEqualTo(90.25);
        assertThat(parsed.get("truth").doubleValue()).isEqualTo(75.0);
      }
    } finally {
      Locale.setDefault(original);
      Locale.setDefault(Locale.Category.FORMAT, originalFormat);
    }
  }
}
