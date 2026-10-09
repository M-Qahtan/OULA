package com.oula.advisory;
import java.util.List;
public record HumanReviewTimeline(HumanReviewCase reviewCase, List<HumanReviewEvent> history) {
    public HumanReviewTimeline {
        history = List.copyOf(history);
    }
}
