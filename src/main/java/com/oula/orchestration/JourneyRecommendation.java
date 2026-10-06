package com.oula.orchestration;
import com.oula.intelligence.Recommendation;
import com.oula.matching.MatchRunOutcome;
public record JourneyRecommendation(MatchRunOutcome matches, Recommendation recommendation) {}
