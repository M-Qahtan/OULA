package com.oula.matching.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LifeFitEngineTest {
    @Test
    void returnsExplainableDeterministicScore() {
        var result = new LifeFitEngine().score(Map.of(
                LifeFitDimension.FINANCIAL, 90.0,
                LifeFitDimension.LOCATION, 85.0,
                LifeFitDimension.HOUSEHOLD, 92.0,
                LifeFitDimension.MOBILITY, 75.0,
                LifeFitDimension.FUTURE, 88.0,
                LifeFitDimension.PROPERTY, 91.0,
                LifeFitDimension.RISK, 80.0
        ), 0.92);

        assertTrue(result.score() > 80);
        assertTrue(result.confidence() > 0.8);
        assertEquals(7, result.contributions().size());
    }
}
